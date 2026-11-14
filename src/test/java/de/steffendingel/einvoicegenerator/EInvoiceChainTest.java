package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.einvoice.MustangValidator;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlEmbedder;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlGenerator;
import de.steffendingel.einvoicegenerator.format.GermanFormat;
import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;
import de.steffendingel.einvoicegenerator.pdf.OpenHtmlToPdfConverter;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxPageAppender;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxTextVerifier;
import de.steffendingel.einvoicegenerator.pdf.VeraPdfValidator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mustangproject.ZUGFeRD.ZUGFeRDImporter;

/**
 * Steps ② to ⑨ in the order of the README diagram, as InvoiceService will run them later.
 */
class EInvoiceChainTest {

    private static Invoice invoice;
    private static Timesheet timesheet;
    private static byte[] mergedPdf;
    private static byte[] xml;
    private static byte[] eInvoice;

    @BeforeAll
    static void runTheChain() throws IOException {
        invoice = TestData.invoice();
        OpenHtmlToPdfConverter converter = new OpenHtmlToPdfConverter(null);
        timesheet = new Timesheet("stundennachweis-2026-10.pdf", "Stundennachweis 10/2026", converter.convert(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nachweis</title></head>"
                        + "<body style=\"font-family: Carlito\"><h1>Stundennachweis 10/2026</h1><p>37,5 Stunden</p></body></html>"));

        String xhtml = new FreemarkerHtmlRenderer().renderHtml(invoice, List.of(timesheet.label()));             // ②
        byte[] invoicePdf = converter.convert(xhtml);                                                             // ③
        ValidationResult text = new PdfBoxTextVerifier().verify(invoicePdf,
                List.of("Rechnung Nr. " + invoice.number(), GermanFormat.money(invoice.grossAmount())));          // ④
        assertThat(text.valid()).as(text.toString()).isTrue();
        mergedPdf = new PdfBoxPageAppender().append(invoicePdf, List.of(timesheet.pdf()));                        // ⑤
        ValidationResult pdfA = new VeraPdfValidator().validate(mergedPdf);                                       // ⑥
        assertThat(pdfA.valid()).as(pdfA.toString()).isTrue();
        xml = new ZugferdXmlGenerator().generateXml(invoice, List.of(timesheet));                                 // ⑦
        eInvoice = new ZugferdXmlEmbedder().embed(mergedPdf, xml);                                                // ⑧
    }

    @Test
    void resultIsAValidEInvoiceWithoutWarnings() throws IOException {
        ValidationResult result = new MustangValidator().validate(eInvoice);                                      // ⑨

        assertThat(result.problems()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).isEqualTo("valid e-invoice (ZUGFeRD EN 16931)");
    }

    @Test
    void resultIsStillPdfA3b() throws IOException {
        assertThat(new VeraPdfValidator().validate(eInvoice).valid()).isTrue();
    }

    @Nested
    class Embedding {

        @Test
        void embedsTheXmlUnchanged() throws IOException {
            assertThat(new ZUGFeRDImporter(new ByteArrayInputStream(eInvoice)).getRawXML()).isEqualTo(xml);
        }

        @Test
        void keepsAllPagesAndTheVisibleInvoice() throws IOException {
            try (PDDocument document = Loader.loadPDF(eInvoice)) {
                assertThat(document.getNumberOfPages()).isEqualTo(2);
                assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Rechnung Nr. 302");
            }
        }

        @Test
        void namesTheToolWithVersionAndProjectUrl() throws IOException {
            String tool = "E-Invoice Generator " + AppInfo.VERSION;
            String metadata;
            try (PDDocument document = Loader.loadPDF(eInvoice)) {
                assertThat(document.getDocumentInformation().getProducer()).isEqualTo(tool);
                assertThat(document.getDocumentInformation().getCreator()).isEqualTo(tool);
                metadata = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            }
            assertThat(metadata)
                    .contains("<pdf:Producer>" + tool + "</pdf:Producer>")
                    .contains("<xmp:CreatorTool>" + tool + " (https://github.com/sdingel/e-invoice-generator)</xmp:CreatorTool>");
        }

        @Test
        void declaresPdfA3bAndTheZugferdProfile() throws IOException {
            String metadata;
            try (PDDocument document = Loader.loadPDF(eInvoice)) {
                metadata = new String(document.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            }
            assertThat(metadata)
                    .containsPattern("pdfaid:part[^0-9]*3")
                    .containsPattern("pdfaid:conformance[^A-Z]*B")
                    .contains("EN 16931")
                    .contains("factur-x.xml");
        }

        @Test
        void rejectsInputThatIsNotAPdf() {
            assertThatThrownBy(() -> new ZugferdXmlEmbedder().embed("no pdf".getBytes(), xml))
                    .isInstanceOf(IOException.class);
        }
    }

    @Nested
    class Validation {

        @Test
        void findsAContradictionInTheXml() throws IOException {
            String manipulated = new String(xml, StandardCharsets.UTF_8).replace(
                    "<ram:GrandTotalAmount>4239.38</ram:GrandTotalAmount>",
                    "<ram:GrandTotalAmount>9999.99</ram:GrandTotalAmount>");
            assertThat(manipulated).contains("9999.99");

            ValidationResult result = new MustangValidator().validate(
                    new ZugferdXmlEmbedder().embed(mergedPdf, manipulated.getBytes(StandardCharsets.UTF_8)));

            assertThat(result.valid()).isFalse();
            assertThat(result.summary()).isEqualTo("invalid e-invoice");
            assertThat(result.problems()).anySatisfy(problem -> assertThat(problem).startsWith("error:"));
        }

        @Test
        void treatsWarningsAsInvalid() throws IOException {
            // Without the delivery date, Mustang would write an empty delivery element → warning PEPPOL-EN16931-R008
            String withoutDeliveryDate = new String(xml, StandardCharsets.UTF_8).replaceAll(
                    "(?s)<ram:ActualDeliverySupplyChainEvent>.*?</ram:ActualDeliverySupplyChainEvent>", "");

            ValidationResult result = new MustangValidator().validate(withoutDeliveryDate.getBytes(StandardCharsets.UTF_8));

            assertThat(result.valid()).isFalse();
            assertThat(result.problems()).anySatisfy(problem -> assertThat(problem).startsWith("warning:").contains("R008"));
        }

        @Test
        void rejectsAPdfWithoutXml() throws IOException {
            assertThat(new MustangValidator().validate(mergedPdf).valid()).isFalse();
        }
    }
}
