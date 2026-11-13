package de.steffendingel.einvoicegenerator.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PdfBoxPageAppenderTest {

    private static byte[] invoicePdf;

    private final PdfBoxPageAppender appender = new PdfBoxPageAppender();

    @BeforeAll
    static void renderInvoice() throws IOException {
        String xhtml = new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(), List.of("Stundennachweis 10/2026"));
        invoicePdf = new OpenHtmlToPdfConverter(null).convert(xhtml);
    }

    @Test
    void appendsAllPagesInOrder() throws IOException {
        byte[] merged = appender.append(invoicePdf, List.of(TimesheetPdfs.plain(2), TimesheetPdfs.plain(1)));

        try (PDDocument document = Loader.loadPDF(merged)) {
            assertThat(document.getNumberOfPages()).isEqualTo(4);
            assertThat(pageText(document, 1)).contains("Rechnung Nr. 302");
            assertThat(pageText(document, 2)).contains("Seite 1");
            assertThat(pageText(document, 3)).contains("Seite 2");
            assertThat(pageText(document, 4)).contains("Seite 1");
        }
    }

    @Test
    void keepsMetadataAndColorProfileOfTheInvoice() throws IOException {
        byte[] merged = appender.append(invoicePdf, List.of(TimesheetPdfs.plain(1)));

        try (PDDocument document = Loader.loadPDF(merged)) {
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Rechnung Nr. 302");
            assertThat(document.getDocumentCatalog().getMetadata()).isNotNull();
            assertThat(document.getDocumentCatalog().getOutputIntents()).hasSize(1);
        }
    }

    @Test
    void keepsPageSizeAndOrientation() throws IOException {
        byte[] merged = appender.append(invoicePdf, List.of(TimesheetPdfs.landscape()));

        try (PDDocument document = Loader.loadPDF(merged)) {
            PDRectangle invoicePage = document.getPage(0).getMediaBox();
            PDRectangle timesheetPage = document.getPage(1).getMediaBox();
            assertThat(invoicePage.getHeight()).isGreaterThan(invoicePage.getWidth());
            assertThat(timesheetPage.getWidth()).isGreaterThan(timesheetPage.getHeight());
        }
    }

    @Test
    void returnsTheInvoiceUnchangedWithoutAttachments() throws IOException {
        assertThat(appender.append(invoicePdf, List.of())).isEqualTo(invoicePdf);
    }

    @Test
    void rejectsPasswordProtectedAttachments() {
        assertThatThrownBy(() -> appender.append(invoicePdf, List.of(TimesheetPdfs.passwordProtected())))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("attachment 1 is password-protected");
    }

    @Test
    void rejectsDamagedAttachments() throws IOException {
        byte[] damaged = "%PDF-1.7 this is not a PDF".getBytes();

        assertThatThrownBy(() -> appender.append(invoicePdf, List.of(TimesheetPdfs.plain(1), damaged)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("attachment 2 is not a readable PDF");
    }

    private static String pageText(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document);
    }
}
