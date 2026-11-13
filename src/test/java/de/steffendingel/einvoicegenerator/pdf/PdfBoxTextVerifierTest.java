package de.steffendingel.einvoicegenerator.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PdfBoxTextVerifierTest {

    private static byte[] examplePdf;

    private final PdfBoxTextVerifier verifier = new PdfBoxTextVerifier();

    @BeforeAll
    static void renderExample() throws IOException {
        String xhtml = new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(), List.of("Stundennachweis 10/2026"));
        examplePdf = new OpenHtmlToPdfConverter(null).convert(xhtml);
    }

    @Test
    void findsInvoiceNumberAndTotal() throws IOException {
        ValidationResult result = verifier.verify(examplePdf, List.of("Rechnung Nr. 302", "4.239,38 €"));

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).isEqualTo("all 2 expected texts found");
    }

    @Test
    void findsTextThatWrapsAcrossLines() throws IOException {
        // "Beratungs- und Entwicklungsleistungen" is split over two lines in the table
        assertThat(verifier.verify(examplePdf, List.of("Beratungs- und Entwicklungsleistungen")).valid()).isTrue();
    }

    @Test
    void reportsEveryMissingText() throws IOException {
        ValidationResult result = verifier.verify(examplePdf, List.of("Rechnung Nr. 302", "Rechnung Nr. 999", "1,00 €"));

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).containsExactly("missing text: \"Rechnung Nr. 999\"", "missing text: \"1,00 €\"");
    }

    @Test
    void reportsAPdfWithoutTextClearly() throws IOException {
        ValidationResult result = verifier.verify(emptyPdf(), List.of("Rechnung Nr. 302"));

        assertThat(result.valid()).isFalse();
        assertThat(result.summary()).isEqualTo("PDF contains no text");
        assertThat(result.problems()).singleElement().asString().contains("font");
    }

    private static byte[] emptyPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }
}
