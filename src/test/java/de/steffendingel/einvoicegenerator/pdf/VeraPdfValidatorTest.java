package de.steffendingel.einvoicegenerator.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Step ⑥, mostly applied to the result of step ⑤ as in the real flow. */
class VeraPdfValidatorTest {

    private static byte[] invoicePdf;

    private final VeraPdfValidator validator = new VeraPdfValidator();
    private final PdfBoxPageAppender appender = new PdfBoxPageAppender();

    @BeforeAll
    static void renderInvoice() throws IOException {
        String xhtml = new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(), List.of("Stundennachweis 10/2026"));
        invoicePdf = new OpenHtmlToPdfConverter(null).convert(xhtml);
    }

    private ValidationResult validateWith(byte[] timesheet) throws IOException {
        return validator.validate(appender.append(invoicePdf, List.of(timesheet)));
    }

    @Test
    void acceptsTheRenderedInvoice() throws IOException {
        ValidationResult result = validator.validate(invoicePdf);

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).isEqualTo("PDF/A-3b compliant");
    }

    @Nested
    class AfterAppending {

        @Test
        void acceptsAPlainPdfWithEmbeddedFontsLikeAnExcelExport() throws IOException {
            assertThat(validateWith(TimesheetPdfs.plain(2)).valid()).isTrue();
        }

        @Test
        void acceptsATimesheetThatIsAlreadyPdfA() throws IOException {
            byte[] pdfATimesheet = new OpenHtmlToPdfConverter(null).convert(
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nachweis</title></head>"
                            + "<body style=\"font-family: Carlito\"><p>Stundennachweis 10/2026</p></body></html>");

            assertThat(validateWith(pdfATimesheet).valid()).isTrue();
        }

        @Test
        void acceptsLandscapePages() throws IOException {
            assertThat(validateWith(TimesheetPdfs.landscape()).valid()).isTrue();
        }

        @Test
        void rejectsFontsThatAreNotEmbedded() throws IOException {
            ValidationResult result = validateWith(TimesheetPdfs.withFont(TimesheetPdfs.Font.NOT_EMBEDDED));

            assertThat(result.valid()).isFalse();
            assertThat(result.summary()).isEqualTo("not PDF/A-3b compliant");
            assertThat(result.problems()).anySatisfy(problem -> assertThat(problem).containsIgnoringCase("embedded"));
        }

        @Test
        void rejectsCmykColors() throws IOException {
            ValidationResult result = validateWith(TimesheetPdfs.withColor(TimesheetPdfs.Color.CMYK));

            assertThat(result.valid()).isFalse();
            assertThat(result.problems()).anySatisfy(problem -> assertThat(problem).contains("DeviceCMYK"));
        }
    }

    @Test
    void rejectsAPlainPdfOnItsOwn() throws IOException {
        // Without the invoice there is no metadata (PDF/A identification) and no output intent
        ValidationResult result = validator.validate(TimesheetPdfs.plain(1));

        assertThat(result.valid()).isFalse();
        assertThat(result.problems())
                .anySatisfy(problem -> assertThat(problem).contains("Metadata"))
                .anySatisfy(problem -> assertThat(problem).contains("OutputIntent"));
    }

    @Test
    void reportsUnreadableInput() {
        assertThatThrownBy(() -> validator.validate("not a pdf".getBytes()))
                .isInstanceOf(IOException.class);
    }
}
