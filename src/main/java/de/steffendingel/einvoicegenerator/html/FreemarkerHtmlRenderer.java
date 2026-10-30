package de.steffendingel.einvoicegenerator.html;

import de.steffendingel.einvoicegenerator.format.GermanFormat;
import de.steffendingel.einvoicegenerator.model.Invoice;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateModelException;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Step ② with FreeMarker: fills the built-in template {@code templates/invoice.ftlh} or a template file
 * configured by the user.
 *
 * <p>The template gets the records of the model ({@code invoice}, {@code seller}, {@code customer}),
 * the attachment labels, the optional logo and {@link GermanFormat} as {@code format}. HTML escaping is
 * active because templates use the {@code .ftlh} extension.
 */
public final class FreemarkerHtmlRenderer implements InvoiceHtmlRenderer {

    /** Records are accessible as {@code ${customer.name}} only from this version on. */
    private static final freemarker.template.Version VERSION = Configuration.VERSION_2_3_35;

    private static final String BUILT_IN_DIRECTORY = "/templates";
    private static final String BUILT_IN_TEMPLATE = "invoice.ftlh";

    private final Configuration configuration;
    private final String templateName;
    private final Path logo;

    /** Uses the built-in template and no logo. */
    public FreemarkerHtmlRenderer() throws IOException {
        this(null, null);
    }

    /**
     * @param template template file ({@code .ftlh}) or {@code null} for the built-in template
     * @param logo     logo file or {@code null} for no logo
     */
    public FreemarkerHtmlRenderer(Path template, Path logo) throws IOException {
        this.configuration = new Configuration(VERSION);
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setRecognizeStandardFileExtensions(true); // .ftlh → HTML escaping
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        if (template == null) {
            configuration.setClassForTemplateLoading(FreemarkerHtmlRenderer.class, BUILT_IN_DIRECTORY);
            this.templateName = BUILT_IN_TEMPLATE;
        } else {
            if (!Files.isRegularFile(template)) {
                throw new IOException("template not found: " + template);
            }
            Path absolute = template.toAbsolutePath();
            configuration.setDirectoryForTemplateLoading(absolute.getParent().toFile());
            this.templateName = absolute.getFileName().toString();
        }
        if (logo != null && !Files.isRegularFile(logo)) {
            throw new IOException("logo not found: " + logo);
        }
        this.logo = logo;
    }

    @Override
    public String renderHtml(Invoice invoice, List<String> attachmentLabels) throws IOException {
        Objects.requireNonNull(invoice, "invoice must not be null");
        Map<String, Object> model = new HashMap<>();
        model.put("invoice", invoice);
        model.put("seller", invoice.seller());
        model.put("customer", invoice.customer());
        model.put("attachments", attachmentLabels == null ? List.of() : List.copyOf(attachmentLabels));
        model.put("format", formatModel());
        if (logo != null) {
            model.put("logo", logo.toAbsolutePath().toUri().toString());
        }

        Template template = configuration.getTemplate(templateName);
        StringWriter html = new StringWriter();
        try {
            template.process(model, html);
        } catch (TemplateException e) {
            // e.g. "The following has evaluated to null or missing: ==> customer.nam [in template ... at line 12]"
            throw new IOException("error in template " + templateName + ": " + e.getMessageWithoutStackTop(), e);
        }
        return html.toString();
    }

    /** Makes the static methods of {@link GermanFormat} callable as {@code format.money(...)}. */
    private static Object formatModel() throws IOException {
        try {
            return new DefaultObjectWrapperBuilder(VERSION).build()
                    .getStaticModels().get(GermanFormat.class.getName());
        } catch (TemplateModelException e) {
            throw new IOException("cannot expose GermanFormat to the template", e);
        }
    }
}
