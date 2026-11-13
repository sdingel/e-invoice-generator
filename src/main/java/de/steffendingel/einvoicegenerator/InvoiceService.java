package de.steffendingel.einvoicegenerator;

import de.steffendingel.einvoicegenerator.archive.InvoiceArchive;
import de.steffendingel.einvoicegenerator.einvoice.EInvoiceValidator;
import de.steffendingel.einvoicegenerator.einvoice.EInvoiceXmlEmbedder;
import de.steffendingel.einvoicegenerator.einvoice.EInvoiceXmlGenerator;
import de.steffendingel.einvoicegenerator.format.GermanFormat;
import de.steffendingel.einvoicegenerator.html.InvoiceHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;
import de.steffendingel.einvoicegenerator.numbering.InvoiceNumberSource;
import de.steffendingel.einvoicegenerator.pdf.HtmlToPdfConverter;
import de.steffendingel.einvoicegenerator.pdf.PdfAValidator;
import de.steffendingel.einvoicegenerator.pdf.PdfPageAppender;
import de.steffendingel.einvoicegenerator.pdf.PdfTextVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs the steps ① to ⑪ of the README diagram. Knows only the interfaces of the steps; the
 * implementations are passed in when the application starts.
 *
 * <p>Nothing is written before all checks (④, ⑥, ⑨) have passed: if a step fails, no file is stored
 * and the invoice number is not used up.
 */
public final class InvoiceService {

    /** Receives a call for every step that was completed, e.g. to show progress in the dialog. */
    @FunctionalInterface
    public interface ProgressListener {
        void completed(Step step);
    }

    /** An empty page that a timesheet is appended to on trial (see {@link #checkTimesheet}). */
    private static final String EMPTY_PAGE = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Check</title>"
            + "</head><body></body></html>";

    private final InvoiceNumberSource numbers;
    private final InvoiceCalculator calculator;
    private final InvoiceHtmlRenderer htmlRenderer;
    private final HtmlToPdfConverter pdfConverter;
    private final PdfTextVerifier textVerifier;
    private final PdfPageAppender pageAppender;
    private final PdfAValidator pdfAValidator;
    private final EInvoiceXmlGenerator xmlGenerator;
    private final EInvoiceXmlEmbedder xmlEmbedder;
    private final EInvoiceValidator eInvoiceValidator;
    private final InvoiceArchive archive;

    private byte[] emptyPage;

    /** The parameters follow the order of the steps. */
    public InvoiceService(InvoiceNumberSource numbers, InvoiceCalculator calculator, InvoiceHtmlRenderer htmlRenderer,
                          HtmlToPdfConverter pdfConverter, PdfTextVerifier textVerifier, PdfPageAppender pageAppender,
                          PdfAValidator pdfAValidator, EInvoiceXmlGenerator xmlGenerator, EInvoiceXmlEmbedder xmlEmbedder,
                          EInvoiceValidator eInvoiceValidator, InvoiceArchive archive) {
        this.numbers = Objects.requireNonNull(numbers, "numbers must not be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null");
        this.htmlRenderer = Objects.requireNonNull(htmlRenderer, "htmlRenderer must not be null");
        this.pdfConverter = Objects.requireNonNull(pdfConverter, "pdfConverter must not be null");
        this.textVerifier = Objects.requireNonNull(textVerifier, "textVerifier must not be null");
        this.pageAppender = Objects.requireNonNull(pageAppender, "pageAppender must not be null");
        this.pdfAValidator = Objects.requireNonNull(pdfAValidator, "pdfAValidator must not be null");
        this.xmlGenerator = Objects.requireNonNull(xmlGenerator, "xmlGenerator must not be null");
        this.xmlEmbedder = Objects.requireNonNull(xmlEmbedder, "xmlEmbedder must not be null");
        this.eInvoiceValidator = Objects.requireNonNull(eInvoiceValidator, "eInvoiceValidator must not be null");
        this.archive = Objects.requireNonNull(archive, "archive must not be null");
    }

    /**
     * Checks right after input whether the timesheet can be used: steps ⑤ and ⑥ on trial, with an empty
     * page instead of the invoice.
     *
     * @return invalid with the reasons, e.g. a password-protected file or CMYK colors
     */
    public ValidationResult checkTimesheet(Timesheet timesheet) throws IOException {
        Objects.requireNonNull(timesheet, "timesheet must not be null");
        byte[] merged;
        try {
            merged = pageAppender.append(emptyPage(), List.of(timesheet.pdf()));
        } catch (IOException e) {
            return ValidationResult.invalid("timesheet cannot be appended", List.of(e.getMessage()));
        }
        return pdfAValidator.validate(merged);
    }

    /** Warning about double billing: an invoice that is already stored for this customer and period. */
    public Optional<String> findExisting(Customer customer, BillingPeriod period) throws IOException {
        return archive.findExisting(customer, period);
    }

    /** Step ①: takes the next invoice number (without using it up) and calculates the invoice. */
    public Invoice prepare(InvoiceRequest request) throws IOException {
        return calculator.calculate(request, numbers.peekNext());
    }

    /** Step ② on its own, e.g. as HTML preview. */
    public String previewHtml(Invoice invoice, List<Timesheet> timesheets) throws IOException {
        return htmlRenderer.renderHtml(invoice, labels(timesheets));
    }

    /**
     * Steps ② to ⑪: creates, checks and stores the e-invoice and uses up the invoice number.
     *
     * @return where the e-invoice was stored
     * @throws InvoiceGenerationException with the failed step; nothing is stored if a step up to ⑨ fails
     */
    public String generate(Invoice invoice, List<Timesheet> timesheets, ProgressListener progress)
            throws InvoiceGenerationException {
        Objects.requireNonNull(invoice, "invoice must not be null");
        List<Timesheet> attachments = timesheets == null ? List.of() : List.copyOf(timesheets);
        ProgressListener listener = progress == null ? step -> { } : progress;

        checkNumberIsStillNext(invoice);
        listener.completed(Step.CALCULATE_INVOICE);

        String xhtml = run(Step.FILL_TEMPLATE, () -> htmlRenderer.renderHtml(invoice, labels(attachments)), listener);
        byte[] invoicePdf = run(Step.RENDER_PDF, () -> pdfConverter.convert(xhtml), listener);
        check(Step.CHECK_TEXT, () -> textVerifier.verify(invoicePdf, expectedTexts(invoice)), listener);
        byte[] merged = run(Step.APPEND_TIMESHEETS,
                () -> pageAppender.append(invoicePdf, attachments.stream().map(Timesheet::pdf).toList()), listener);
        check(Step.CHECK_PDF_A, () -> pdfAValidator.validate(merged), listener);
        byte[] xml = run(Step.CREATE_XML, () -> xmlGenerator.generateXml(invoice, attachments), listener);
        byte[] eInvoice = run(Step.EMBED_XML, () -> xmlEmbedder.embed(merged, xml), listener);
        check(Step.VALIDATE_E_INVOICE, () -> eInvoiceValidator.validate(eInvoice), listener);
        String stored = run(Step.STORE_INVOICE, () -> archive.store(invoice, eInvoice), listener);
        try {
            numbers.markUsed(invoice.number());
        } catch (IOException e) {
            throw new InvoiceGenerationException(Step.INCREMENT_NUMBER, "the invoice was stored as " + stored
                    + ", but the invoice counter could not be updated: " + e.getMessage()
                    + ". Set it to the next number by hand.", e);
        }
        listener.completed(Step.INCREMENT_NUMBER);
        return stored;
    }

    /** Another invoice may have been created between {@link #prepare} and the confirmation. */
    private void checkNumberIsStillNext(Invoice invoice) throws InvoiceGenerationException {
        String next;
        try {
            next = numbers.peekNext();
        } catch (IOException e) {
            throw new InvoiceGenerationException(Step.CALCULATE_INVOICE, e.getMessage(), e);
        }
        if (!next.equals(invoice.number())) {
            throw new InvoiceGenerationException(Step.CALCULATE_INVOICE, "invoice number " + invoice.number()
                    + " is no longer the next number (now " + next + "); please start again", null);
        }
    }

    /** Texts that must be in the rendered PDF (step ④), formatted like in the template. */
    private static List<String> expectedTexts(Invoice invoice) {
        return List.of(invoice.number(), GermanFormat.money(invoice.grossAmount()));
    }

    private static List<String> labels(List<Timesheet> timesheets) {
        return timesheets == null ? List.of() : timesheets.stream().map(Timesheet::label).toList();
    }

    private byte[] emptyPage() throws IOException {
        if (emptyPage == null) {
            emptyPage = pdfConverter.convert(EMPTY_PAGE);
        }
        return emptyPage;
    }

    @FunctionalInterface
    private interface Action<T> {
        T run() throws IOException;
    }

    private static <T> T run(Step step, Action<T> action, ProgressListener listener) throws InvoiceGenerationException {
        T result;
        try {
            result = action.run();
        } catch (IOException | RuntimeException e) {
            throw new InvoiceGenerationException(step, e.getMessage(), e);
        }
        listener.completed(step);
        return result;
    }

    private static void check(Step step, Action<ValidationResult> check, ProgressListener listener)
            throws InvoiceGenerationException {
        // Report the step only after the check has passed
        ValidationResult result = run(step, check, ignored -> { });
        if (!result.valid()) {
            throw new InvoiceGenerationException(step, result.summary(), result.problems(), null);
        }
        listener.completed(step);
    }
}
