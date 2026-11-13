package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.archive.InvoiceArchive;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;
import de.steffendingel.einvoicegenerator.numbering.InvoiceNumberSource;
import de.steffendingel.einvoicegenerator.pdf.PdfAValidator;
import de.steffendingel.einvoicegenerator.pdf.PdfPageAppender;
import de.steffendingel.einvoicegenerator.pdf.PdfTextVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The flow of {@link InvoiceService} with simple stand-ins for the steps, so that every failure can be
 * provoked. The real implementations are tested in {@link InvoiceServiceIntegrationTest}.
 */
class InvoiceServiceTest {

    private static final Timesheet TIMESHEET = new Timesheet("ts.pdf", "Stundennachweis 10/2026", "TS".getBytes());

    /** Invoice counter in memory. */
    static final class Numbers implements InvoiceNumberSource {
        long next = 302;

        @Override
        public String peekNext() {
            return String.valueOf(next);
        }

        @Override
        public void markUsed(String number) throws IOException {
            if (!number.equals(peekNext())) {
                throw new IOException("unexpected number " + number);
            }
            next++;
        }
    }

    /** Archive in memory: file name → content. */
    static final class Archive implements InvoiceArchive {
        final Map<String, byte[]> files = new LinkedHashMap<>();

        @Override
        public Optional<String> findExisting(Customer customer, BillingPeriod period) {
            return files.keySet().stream().filter(name -> name.contains(customer.id())).findFirst();
        }

        @Override
        public String store(Invoice invoice, byte[] eInvoice) {
            String name = invoice.number() + "_" + invoice.customer().id() + ".pdf";
            files.put(name, eInvoice);
            return name;
        }
    }

    private final Numbers numbers = new Numbers();
    private final Archive archive = new Archive();
    private final List<Step> completed = new ArrayList<>();

    private PdfTextVerifier textVerifier = (pdf, expected) -> ValidationResult.valid("text ok");
    private PdfPageAppender appender = (base, attachments) -> concat(base, attachments.toArray(byte[][]::new));
    private PdfAValidator pdfAValidator = pdf -> ValidationResult.valid("PDF/A-3b compliant");
    private de.steffendingel.einvoicegenerator.einvoice.EInvoiceValidator eInvoiceValidator =
            eInvoice -> ValidationResult.valid("valid e-invoice");

    private InvoiceService service() {
        return new InvoiceService(numbers, new InvoiceCalculator(TestData.seller()),
                (invoice, labels) -> "<html>" + invoice.number() + " " + labels + "</html>",
                xhtml -> ("PDF[" + xhtml + "]").getBytes(StandardCharsets.UTF_8),
                textVerifier, appender, pdfAValidator,
                (invoice, timesheets) -> ("<xml>" + invoice.number() + "</xml>").getBytes(StandardCharsets.UTF_8),
                (pdf, xml) -> concat(pdf, xml),
                eInvoiceValidator, archive);
    }

    private Invoice prepared() throws IOException {
        return service().prepare(TestData.request("37.5"));
    }

    @Test
    void prepareTakesTheNextNumberWithoutUsingItUp() throws IOException {
        Invoice invoice = prepared();

        assertThat(invoice.number()).isEqualTo("302");
        assertThat(invoice.grossAmount()).isEqualByComparingTo("4239.38");
        assertThat(numbers.peekNext()).isEqualTo("302");
    }

    @Test
    void generateRunsAllStepsInOrderAndUsesUpTheNumber() throws Exception {
        String stored = service().generate(prepared(), List.of(TIMESHEET), completed::add);

        assertThat(completed).containsExactly(Step.values());
        assertThat(stored).isEqualTo("302_beispiel.pdf");
        assertThat(new String(archive.files.get(stored), StandardCharsets.UTF_8))
                .isEqualTo("PDF[<html>302 [Stundennachweis 10/2026]</html>]TS<xml>302</xml>");
        assertThat(numbers.peekNext()).isEqualTo("303");
    }

    @Test
    void generateWorksWithoutTimesheetsAndProgressListener() throws Exception {
        assertThat(service().generate(prepared(), List.of(), null)).isEqualTo("302_beispiel.pdf");
    }

    @Test
    void checksForTheInvoiceNumberAndTheTotalInTheText() throws Exception {
        List<String> checked = new ArrayList<>();
        textVerifier = (pdf, expected) -> {
            checked.addAll(expected);
            return ValidationResult.valid("ok");
        };

        service().generate(prepared(), List.of(), null);

        assertThat(checked).containsExactly("302", "4.239,38 €");
    }

    @Nested
    class WhenAStepFails {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Step.class, names = {"CHECK_TEXT", "CHECK_PDF_A", "VALIDATE_E_INVOICE"})
        void aFailedCheckStoresNothingAndKeepsTheNumber(Step failingStep) throws IOException {
            ValidationResult invalid = ValidationResult.invalid("check failed", List.of("problem 1", "problem 2"));
            switch (failingStep) {
                case CHECK_TEXT -> textVerifier = (pdf, expected) -> invalid;
                case CHECK_PDF_A -> pdfAValidator = pdf -> invalid;
                default -> eInvoiceValidator = eInvoice -> invalid;
            }
            Invoice invoice = prepared();

            assertThatThrownBy(() -> service().generate(invoice, List.of(TIMESHEET), completed::add))
                    .isInstanceOfSatisfying(InvoiceGenerationException.class, e -> {
                        assertThat(e.step()).isEqualTo(failingStep);
                        assertThat(e.getMessage()).isEqualTo(failingStep.number() + " check failed");
                        assertThat(e.problems()).containsExactly("problem 1", "problem 2");
                    });
            assertThat(completed).doesNotContain(failingStep).doesNotContain(Step.STORE_INVOICE);
            assertThat(archive.files).isEmpty();
            assertThat(numbers.peekNext()).isEqualTo("302");
        }

        @Test
        void anExceptionInAStepNamesTheStep() throws IOException {
            appender = (base, attachments) -> {
                throw new IOException("attachment 1 is password-protected");
            };
            Invoice invoice = prepared();

            assertThatThrownBy(() -> service().generate(invoice, List.of(TIMESHEET), null))
                    .isInstanceOfSatisfying(InvoiceGenerationException.class, e -> {
                        assertThat(e.step()).isEqualTo(Step.APPEND_TIMESHEETS);
                        assertThat(e.getMessage()).isEqualTo("⑤ attachment 1 is password-protected");
                    });
            assertThat(archive.files).isEmpty();
        }

        @Test
        void refusesANumberThatIsNoLongerTheNextOne() throws Exception {
            Invoice invoice = prepared();
            numbers.next = 303; // another invoice was created in the meantime

            assertThatThrownBy(() -> service().generate(invoice, List.of(), null))
                    .isInstanceOfSatisfying(InvoiceGenerationException.class, e -> {
                        assertThat(e.step()).isEqualTo(Step.CALCULATE_INVOICE);
                        assertThat(e.getMessage()).contains("302 is no longer the next number (now 303)");
                    });
            assertThat(archive.files).isEmpty();
        }
    }

    @Nested
    class Timesheets {

        @Test
        void checkAppendsOnTrialAndValidates() throws IOException {
            List<byte[]> validated = new ArrayList<>();
            pdfAValidator = pdf -> {
                validated.add(pdf);
                return ValidationResult.valid("PDF/A-3b compliant");
            };

            assertThat(service().checkTimesheet(TIMESHEET).valid()).isTrue();
            assertThat(new String(validated.get(0), StandardCharsets.UTF_8)).startsWith("PDF[").endsWith("TS");
        }

        @Test
        void checkReportsATimesheetThatCannotBeAppended() throws IOException {
            appender = (base, attachments) -> {
                throw new IOException("attachment 1 is password-protected");
            };

            ValidationResult result = service().checkTimesheet(TIMESHEET);

            assertThat(result.valid()).isFalse();
            assertThat(result.problems()).containsExactly("attachment 1 is password-protected");
        }
    }

    @Test
    void findExistingAsksTheArchive() throws Exception {
        assertThat(service().findExisting(TestData.customer(), TestData.invoice().period())).isEmpty();
        service().generate(prepared(), List.of(), null);

        assertThat(service().findExisting(TestData.customer(), TestData.invoice().period())).contains("302_beispiel.pdf");
    }

    private static byte[] concat(byte[] first, byte[]... rest) {
        StringBuilder result = new StringBuilder(new String(first, StandardCharsets.UTF_8));
        for (byte[] part : rest) {
            result.append(new String(part, StandardCharsets.UTF_8));
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }
}
