package de.steffendingel.einvoicegenerator.pdf;

import static de.steffendingel.einvoicegenerator.pdf.PdfTestSupport.pdfAProblems;
import static de.steffendingel.einvoicegenerator.pdf.PdfTestSupport.text;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.Address;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenHtmlToPdfConverterTest {

    private static final List<String> ATTACHMENTS = List.of("Stundennachweis 10/2026");

    private static OpenHtmlToPdfConverter converter;
    private static byte[] examplePdf;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void renderExample() throws IOException {
        converter = new OpenHtmlToPdfConverter(null);
        examplePdf = converter.convert(new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(), ATTACHMENTS));
    }

    private static String html(String fontFamily, String body) {
        return "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Test</title><style>body { font-family: "
                + fontFamily + "; }</style></head><body>" + body + "</body></html>";
    }

    @Nested
    class ExampleInvoice {

        @Test
        void isPdfA3b() throws Exception {
            assertThat(pdfAProblems(examplePdf)).isEmpty();
        }

        @Test
        void containsTheInvoiceText() throws IOException {
            assertThat(text(examplePdf))
                    .contains("Rechnung Nr. 302")
                    .contains("4.239,38 €")
                    .contains("Max Mustermann")
                    .contains("Stundennachweis 10/2026");
        }

        @Test
        void embedsOnlyCarlito() throws IOException {
            try (PDDocument document = Loader.loadPDF(examplePdf)) {
                assertThat(document.getNumberOfPages()).isEqualTo(1);
                PDPage page = document.getPage(0);
                List<PDFont> fonts = new ArrayList<>();
                for (COSName name : page.getResources().getFontNames()) {
                    fonts.add(page.getResources().getFont(name));
                }
                assertThat(fonts).isNotEmpty().allSatisfy(font -> {
                    assertThat(font.isEmbedded()).isTrue();
                    assertThat(font.getName()).contains("Carlito");
                });
            }
        }

        @Test
        void takesTitleAndAuthorFromTheHtml() throws IOException {
            try (PDDocument document = Loader.loadPDF(examplePdf)) {
                assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Rechnung Nr. 302");
                assertThat(document.getDocumentInformation().getAuthor()).isEqualTo("Max Mustermann");
            }
        }
    }

    @Nested
    class Fonts {

        @ParameterizedTest(name = "font-family: {0}")
        @ValueSource(strings = {"'Carlito'", "'Calibri'", "sans-serif", "'Calibri', sans-serif"})
        void rendersTextForTheRegisteredFamilies(String family) throws IOException {
            byte[] pdf = converter.convert(html(family, "<p>Rechnung Nr. 302</p>"));

            assertThat(text(pdf)).contains("Rechnung Nr. 302");
        }

        @Test
        void dropsTextInOtherFamiliesWhichStepFourDetects() throws IOException {
            // Documents why step ④ exists: no error, just no text
            byte[] pdf = converter.convert(html("'Arial'", "<p>Rechnung Nr. 302</p>"));

            assertThat(text(pdf)).isBlank();
            assertThat(new PdfBoxTextVerifier().verify(pdf, List.of("302")).valid()).isFalse();
        }

        @Test
        void supportsSpecialCharacters() throws IOException {
            Customer example = TestData.customer();
            Customer customer = new Customer(example.id(), "Müller & Söhne – Łódź „Test“",
                    new Address("Weg 1", "54321", "Ort", "DE"), null, null, example.hourlyRate(),
                    example.paymentTermDays(), "Beratung", Map.of());
            String xhtml = new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(customer), ATTACHMENTS);

            assertThat(text(converter.convert(xhtml))).contains("Müller & Söhne – Łódź „Test“");
        }

        @Test
        void missingFontFileIsReportedAtConstruction() {
            assertThatThrownBy(() -> new OpenHtmlToPdfConverter("/no-fonts-here/", null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("font not found")
                    .hasMessageContaining("Carlito-Regular.ttf");
        }
    }

    @Nested
    class Logo {

        @Test
        void pdfLogoStaysVectorAndCompliant() throws Exception {
            Path logo = PdfTestSupport.writePdfLogo(tempDir.resolve("logo.pdf"));

            byte[] pdf = converter.convert(new FreemarkerHtmlRenderer(null, logo).renderHtml(TestData.invoice(), ATTACHMENTS));

            assertThat(pdfAProblems(pdf)).isEmpty();
            assertThat(rasterImages(pdf)).isZero();
            assertThat(pdf.length).isGreaterThan(examplePdf.length);
        }

        @Test
        void pngLogoWithTransparencyIsCompliant() throws Exception {
            Path logo = PdfTestSupport.writePngLogo(tempDir.resolve("logo.png"));

            byte[] pdf = converter.convert(new FreemarkerHtmlRenderer(null, logo).renderHtml(TestData.invoice(), ATTACHMENTS));

            assertThat(pdfAProblems(pdf)).isEmpty();
            assertThat(rasterImages(pdf)).isEqualTo(1);
        }

        private int rasterImages(byte[] pdf) throws IOException {
            int count = 0;
            try (PDDocument document = Loader.loadPDF(pdf)) {
                for (COSName name : document.getPage(0).getResources().getXObjectNames()) {
                    PDXObject object = document.getPage(0).getResources().getXObject(name);
                    if (object instanceof PDImageXObject) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    @Nested
    class Input {

        @Test
        void resolvesRelativeImagesAgainstTheBaseDirectory() throws Exception {
            PdfTestSupport.writePngLogo(tempDir.resolve("signature.png"));
            OpenHtmlToPdfConverter withBase = new OpenHtmlToPdfConverter(tempDir);

            byte[] pdf = withBase.convert(html("'Carlito'", "<p>Gruß</p><img src=\"signature.png\" style=\"height: 1cm\"/>"));

            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertThat(document.getPage(0).getResources().getXObjectNames()).isNotEmpty();
            }
        }

        @Test
        void rejectsXhtmlThatIsNotWellFormed() {
            assertThatThrownBy(() -> converter.convert("<html><body><p>open</body></html>"))
                    .isInstanceOf(IOException.class);
        }
    }
}
