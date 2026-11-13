package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;

import de.steffendingel.einvoicegenerator.archive.FileInvoiceArchive;
import de.steffendingel.einvoicegenerator.einvoice.MustangValidator;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlEmbedder;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlGenerator;
import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.numbering.FileInvoiceNumberSource;
import de.steffendingel.einvoicegenerator.pdf.OpenHtmlToPdfConverter;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxPageAppender;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxTextVerifier;
import de.steffendingel.einvoicegenerator.pdf.VeraPdfValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link InvoiceService} with all real implementations, files in a temporary directory. */
class InvoiceServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createsAValidEInvoiceFileAndIncrementsTheCounter() throws Exception {
        Path counter = Files.writeString(tempDir.resolve("invoice-counter"), "302\n");
        Path invoicesDir = tempDir.resolve("invoices");
        OpenHtmlToPdfConverter converter = new OpenHtmlToPdfConverter(null);
        InvoiceService service = new InvoiceService(new FileInvoiceNumberSource(counter),
                new InvoiceCalculator(TestData.seller()), new FreemarkerHtmlRenderer(), converter,
                new PdfBoxTextVerifier(), new PdfBoxPageAppender(), new VeraPdfValidator(),
                new ZugferdXmlGenerator(), new ZugferdXmlEmbedder(), new MustangValidator(),
                new FileInvoiceArchive(invoicesDir, null));
        Timesheet timesheet = new Timesheet("stundennachweis-2026-10.pdf", "Stundennachweis 10/2026", converter.convert(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nachweis</title></head>"
                        + "<body style=\"font-family: Carlito\"><p>Stundennachweis 10/2026</p></body></html>"));

        assertThat(service.checkTimesheet(timesheet).valid()).isTrue();
        Invoice invoice = service.prepare(TestData.request("37.5"));
        List<Step> completed = new ArrayList<>();
        String stored = service.generate(invoice, List.of(timesheet), completed::add);

        assertThat(completed).containsExactly(Step.values());
        assertThat(Path.of(stored)).isEqualTo(
                invoicesDir.resolve("2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf"));
        assertThat(new MustangValidator().validate(Files.readAllBytes(Path.of(stored))).valid()).isTrue();
        assertThat(Files.readString(counter).trim()).isEqualTo("303");
        assertThat(service.findExisting(invoice.customer(), invoice.period())).contains(stored);
    }
}
