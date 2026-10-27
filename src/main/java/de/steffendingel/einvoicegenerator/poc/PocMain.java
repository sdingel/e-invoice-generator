package de.steffendingel.einvoicegenerator.poc;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.mustangproject.BankDetails;
import org.mustangproject.FileAttachment;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.PDFAConformanceLevel;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;
import org.mustangproject.validator.ZUGFeRDValidator;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Proof of Concept (Wegwerf-Code): erzeugt eine hartcodierte ZUGFeRD-Rechnung (EN16931)
 * mit angehängtem Stundennachweis und prüft alle Zwischenergebnisse.
 *
 * <p>Ablauf: FreeMarker → XHTML → PDF/A-3b (openhtmltopdf), Stundennachweis mit veraPDF prüfen,
 * Nachweisseiten mit PDFBox anhängen, XML (inkl. BG-24) mit Mustang einbetten, Ergebnis validieren.
 * Alle Daten sind Beispieldaten.
 */
public class PocMain {

    // --- Modell für das Template (Werte schon fertig formatiert) ---

    public record SellerView(String name, String tagline, String street, String zip, String city,
                             String countryPrefix, String vatId, String taxNumber, String phone,
                             String mobile, String email, String web, String bankName, String iban,
                             String bic) {}

    public record CustomerView(String name, String contact, String street, String zip, String city) {}

    public record InvoiceView(String number, String date, String contractDate, String itemText,
                              String periodFrom, String periodTo, String unit, String quantity,
                              String unitPrice, String lineTotal, String netTotal, String vatRate,
                              String vatTotal, String grossTotal, String dueDate) {}

    private static final Locale DE = Locale.GERMANY;
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yy");
    private static final String FONT_FAMILY = "Carlito";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/poc");
        Files.createDirectories(out);

        // --- Beispieldaten ---
        String number = "302";
        LocalDate invoiceDate = LocalDate.of(2026, 11, 2);
        YearMonth period = YearMonth.of(2026, 10);
        LocalDate dueDate = invoiceDate.plusDays(30);
        BigDecimal hours = new BigDecimal("37.5");
        BigDecimal rate = new BigDecimal("95.00");
        BigDecimal vatPercent = new BigDecimal("19");

        BigDecimal net = hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = net.multiply(vatPercent).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = net.add(vat);

        SellerView seller = new SellerView("Max Mustermann", "Freiberuflicher Beispiel-Berater",
                "Musterstraße 1", "12345", "Musterstadt", "D-", "DE123456789", null,
                "+49 000 0000000", "+49 000 1111111", "max@example.com", "www.example.com",
                "Beispielbank Musterstadt", "DE89 3704 0044 0532 0130 00", "COBADEFFXXX");
        CustomerView customer = new CustomerView("Beispiel GmbH", "z. Hd. Erika Musterfrau",
                "Beispielweg 2", "54321", "Beispielstadt");
        InvoiceView invoice = new InvoiceView(number, invoiceDate.format(DATE_LONG),
                LocalDate.of(2026, 1, 15).format(DATE_LONG), "Beratungs- und Entwicklungsleistungen",
                period.atDay(1).format(DATE_SHORT), period.atEndOfMonth().format(DATE_SHORT), "Stunden",
                decimal(hours), money(rate), money(net), money(net), "19 %", money(vat), money(gross),
                dueDate.format(DATE_SHORT));
        String timesheetLabel = "Stundennachweis " + period.format(DateTimeFormatter.ofPattern("MM/yyyy"));

        Map<String, Object> model = Map.of("seller", seller, "customer", customer, "invoice", invoice,
                "attachments", List.of(timesheetLabel));

        // 1. Template füllen (HTML-Vorschau) und als PDF/A-3b rendern
        String xhtml = renderTemplate("invoice.ftlh", model);
        Files.writeString(out.resolve("1-invoice-preview.html"), xhtml);
        byte[] invoicePdf = renderPdf(xhtml, PdfAConformance.PDFA_3_B);
        Files.write(out.resolve("2-invoice-visual.pdf"), invoicePdf);
        step("Rechnung gerendert", "PDF/A-3b, " + pageCount(invoicePdf) + " Seite(n)");
        report("Rechnung (vor ZUGFeRD)", validatePdfA(invoicePdf));

        // 2. Stundennachweis: hier selbst erzeugt (PDF/A-2b), in echt vom PDF/A-Druckertreiber
        byte[] timesheetPdf = renderPdf(timesheetXhtml(period, hours), PdfAConformance.PDFA_2_B);
        Files.write(out.resolve("3-timesheet.pdf"), timesheetPdf);
        PdfACheck timesheetCheck = validatePdfA(timesheetPdf);
        report("Stundennachweis", timesheetCheck);
        if (!timesheetCheck.compliant()) {
            throw new IllegalStateException("Stundennachweis ist nicht PDF/A-konform, Abbruch.");
        }

        // 3. Nachweisseiten anhängen (vor dem Einbetten des XML)
        byte[] merged = appendPages(invoicePdf, timesheetPdf);
        Files.write(out.resolve("4-merged.pdf"), merged);
        step("Seiten angehängt", pageCount(merged) + " Seiten");
        report("Zusammengefügt (vor ZUGFeRD)", validatePdfA(merged));

        // 4. ZUGFeRD-XML (EN16931) inkl. Nachweis als BG-24 einbetten
        Invoice zf = mustangInvoice(number, invoiceDate, period, dueDate, hours, rate, vatPercent);
        zf.embedFileInXML(new FileAttachment("stundennachweis-" + period + ".pdf", "application/pdf",
                "Data", timesheetPdf).setDescription(timesheetLabel));
        Path result = out.resolve(number + "_beispiel_" + period + ".pdf");
        try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
            exporter.setProducer("e-invoice-generator (PoC)")
                    .setCreator(seller.name())
                    .setProfile(Profiles.getByName("EN16931"))
                    // Mustang schreibt sonst "3u" in die Metadaten; wir erzeugen bewusst 3b
                    .setConformanceLevel(PDFAConformanceLevel.BASIC)
                    .load(merged);
            exporter.setTransaction(zf);
            exporter.export(result.toString());
        }
        step("ZUGFeRD erzeugt", result.toString());

        // 5. Ergebnis prüfen: PDF/A-3 (veraPDF) und XML (Schema + Schematron) mit Mustangs Validator
        report("Fertige E-Rechnung", validatePdfA(Files.readAllBytes(result)));
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String validation = validator.validate(result.toString());
        Files.writeString(out.resolve("5-validation-report.xml"), validation);
        step("Mustang-Validator", validator.wasCompletelyValid()
                ? "gültig" : "NICHT gültig, siehe 5-validation-report.xml");
        step("Summen", "netto " + money(net) + ", USt " + money(vat) + ", brutto " + money(gross));
    }

    // --- FreeMarker ---

    static String renderTemplate(String name, Map<String, Object> model) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_35);
        cfg.setClassForTemplateLoading(PocMain.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setRecognizeStandardFileExtensions(true); // .ftlh → HTML-Escaping
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        Template template = cfg.getTemplate(name);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    // --- openhtmltopdf ---

    static byte[] renderPdf(String xhtml, PdfAConformance conformance) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder()
                .useFastMode()
                .usePdfAConformance(conformance)
                .useColorProfile(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData())
                .withHtmlContent(xhtml, PocMain.class.getResource("/templates/").toString())
                .toStream(bytes);
        font(builder, "Carlito-Regular.ttf", 400, FontStyle.NORMAL);
        font(builder, "Carlito-Bold.ttf", 700, FontStyle.NORMAL);
        font(builder, "Carlito-Italic.ttf", 400, FontStyle.ITALIC);
        font(builder, "Carlito-BoldItalic.ttf", 700, FontStyle.ITALIC);
        builder.run();
        return bytes.toByteArray();
    }

    private static void font(PdfRendererBuilder builder, String file, int weight, FontStyle style) {
        builder.useFont(() -> resource("/fonts/" + file), FONT_FAMILY, weight, style, true);
    }

    private static InputStream resource(String path) {
        InputStream in = PocMain.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Ressource fehlt: " + path);
        }
        return in;
    }

    static String timesheetXhtml(YearMonth period, BigDecimal hours) {
        StringBuilder rows = new StringBuilder();
        BigDecimal perDay = new BigDecimal("7.5");
        BigDecimal remaining = hours;
        for (LocalDate day = period.atDay(1); remaining.signum() > 0; day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() >= 6) {
                continue;
            }
            BigDecimal h = remaining.min(perDay);
            remaining = remaining.subtract(h);
            rows.append("<tr><td>").append(day.format(DATE_LONG)).append("</td><td>Beispieltätigkeit</td>")
                    .append("<td class=\"num\">").append(decimal(h)).append("</td></tr>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="de">
                <head><title>Stundennachweis</title>
                <style>
                  @page { size: A4; margin: 2cm; }
                  body { font-family: 'Carlito'; font-size: 11pt; }
                  table { width: 100%%; border-collapse: collapse; }
                  th, td { border: 0.5pt solid #000; padding: 3pt 5pt; text-align: left; }
                  .num { text-align: right; }
                </style></head>
                <body>
                <h2>Stundennachweis %s (Beispiel)</h2>
                <p>Beispiel GmbH – Beratungs- und Entwicklungsleistungen</p>
                <table>
                  <tr><th>Datum</th><th>Tätigkeit</th><th class="num">Stunden</th></tr>
                %s  <tr><th colspan="2">Summe</th><th class="num">%s</th></tr>
                </table>
                </body></html>
                """.formatted(period.format(DateTimeFormatter.ofPattern("MM/yyyy")), rows, decimal(hours));
    }

    // --- PDFBox ---

    /** Hängt alle Seiten von {@code extra} an {@code base} an; Metadaten und Output Intent von base bleiben. */
    static byte[] appendPages(byte[] base, byte[] extra) throws IOException {
        try (PDDocument target = Loader.loadPDF(base); PDDocument source = Loader.loadPDF(extra)) {
            for (PDPage page : source.getPages()) {
                target.importPage(page);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            target.save(bytes);
            return bytes.toByteArray();
        }
    }

    static int pageCount(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    // --- veraPDF ---

    record PdfACheck(boolean compliant, String flavour, List<String> failures) {}

    static PdfACheck validatePdfA(byte[] pdf) throws Exception {
        VeraGreenfieldFoundryProvider.initialise();
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf));
             PDFAValidator validator = Foundries.defaultInstance().createValidator(parser.getFlavour(), false)) {
            ValidationResult result = validator.validate(parser);
            List<String> failures = result.getTestAssertions().stream()
                    .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                    .map(a -> a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber() + ": " + a.getMessage())
                    .distinct()
                    .limit(10)
                    .toList();
            return new PdfACheck(result.isCompliant(), String.valueOf(result.getPDFAFlavour()), failures);
        }
    }

    // --- Mustang ---

    static Invoice mustangInvoice(String number, LocalDate issued, YearMonth period, LocalDate due,
                                  BigDecimal hours, BigDecimal rate, BigDecimal vatPercent) {
        TradeParty seller = new TradeParty("Max Mustermann", "Musterstraße 1", "12345", "Musterstadt", "DE")
                .addVATID("DE123456789")
                .setEmail("max@example.com")
                .addBankDetails(new BankDetails("DE89370400440532013000", "COBADEFFXXX"));
        TradeParty buyer = new TradeParty("Beispiel GmbH", "Beispielweg 2", "54321", "Beispielstadt", "DE");
        Product product = new Product("Beratungs- und Entwicklungsleistungen", "", "HUR", vatPercent);
        return new Invoice()
                .setNumber(number)
                .setIssueDate(date(issued))
                .setDueDate(date(due))
                .setDeliveryDate(date(period.atEndOfMonth()))
                .setDetailedDeliveryPeriod(date(period.atDay(1)), date(period.atEndOfMonth()))
                .setPaymentTermDescription("Zahlbar bis " + due.format(DATE_LONG) + " ohne Abzug.")
                .setCurrency("EUR")
                .setSender(seller)
                .setRecipient(buyer)
                .addItem(new Item(product, rate, hours));
    }

    /**
     * Mustang formatiert {@link Date} in der Zeitzone der JVM. Mitternacht muss daher in genau dieser
     * Zone liegen, sonst verrutscht das Datum im XML um einen Tag (im PoC so passiert).
     */
    private static Date date(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    // --- Formatierung ---

    static String money(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(DE)).format(value) + " €";
    }

    static String decimal(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(DE)).format(value);
    }

    // --- Ausgabe ---

    private static void step(String what, String detail) {
        System.out.printf("✔ %-30s %s%n", what, detail);
    }

    private static void report(String what, PdfACheck check) {
        step("PDF/A " + what, (check.compliant() ? "konform" : "NICHT konform") + " (" + check.flavour() + ")");
        check.failures().forEach(f -> System.out.println("    - " + f));
    }
}
