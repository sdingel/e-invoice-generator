package de.steffendingel.einvoicegenerator.cli;

import static org.assertj.core.api.Assertions.assertThat;

import de.steffendingel.einvoicegenerator.InvoiceCalculator;
import de.steffendingel.einvoicegenerator.InvoiceService;
import de.steffendingel.einvoicegenerator.archive.InvoiceArchive;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;
import de.steffendingel.einvoicegenerator.numbering.InvoiceNumberSource;
import de.steffendingel.einvoicegenerator.pdf.PdfAValidator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The dialog with prepared answers and simple stand-ins for the steps. */
class InteractiveDialogTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);

    @TempDir
    Path workDir;

    @TempDir
    Path homeDir;

    private final List<Invoice> generated = new ArrayList<>();
    private final List<Timesheet> attachedTimesheets = new ArrayList<>();
    private long nextNumber = 304;
    private Optional<String> existing = Optional.empty();
    private PdfAValidator pdfAValidator = pdf -> ValidationResult.valid("PDF/A-3b compliant");
    private ValidationResult eInvoiceResult = ValidationResult.valid("valid e-invoice");

    @BeforeEach
    void createTimesheet() throws IOException {
        Files.writeString(workDir.resolve("nachweis.pdf"), "%PDF timesheet");
    }

    private InvoiceService service() {
        InvoiceNumberSource numbers = new InvoiceNumberSource() {
            @Override
            public String peekNext() {
                return String.valueOf(nextNumber);
            }

            @Override
            public void markUsed(String number) {
                nextNumber++;
            }
        };
        InvoiceArchive archive = new InvoiceArchive() {
            @Override
            public Optional<String> findExisting(Customer customer, BillingPeriod period) {
                return existing;
            }

            @Override
            public String store(Invoice invoice, byte[] eInvoice) {
                generated.add(invoice);
                return "/invoices/Rechnung " + invoice.number() + ".pdf";
            }
        };
        return new InvoiceService(numbers, new InvoiceCalculator(TestData.seller()),
                (invoice, labels) -> "<html/>", xhtml -> "PDF".getBytes(),
                (pdf, expected) -> ValidationResult.valid("text ok"),
                (base, attachments) -> {
                    StringBuilder merged = new StringBuilder(new String(base));
                    attachments.forEach(pdf -> merged.append(new String(pdf)));
                    return merged.toString().getBytes();
                },
                pdfAValidator,
                (invoice, timesheets) -> {
                    attachedTimesheets.addAll(timesheets);
                    return "<xml/>".getBytes();
                },
                (pdf, xml) -> pdf, eInvoice -> eInvoiceResult, archive);
    }

    private int run(ScriptedConsole console) {
        Customer second = new Customer("muster", "Muster AG", null, TestData.customer().address(), null, null,
                new BigDecimal("110"), 14, "Softwareentwicklung", null);
        return new InteractiveDialog(service(), List.of(TestData.customer(), second), console, TODAY, workDir, homeDir).run();
    }

    @Test
    void createsAnInvoiceWithTheDefaults() {
        ScriptedConsole console = new ScriptedConsole("", "", "", "37,5", "nachweis.pdf", "");

        int exitCode = run(console);

        assertThat(exitCode).isEqualTo(InteractiveDialog.CREATED);
        assertThat(generated).singleElement().satisfies(invoice -> {
            assertThat(invoice.customer().id()).isEqualTo("beispiel");
            assertThat(invoice.invoiceDate()).isEqualTo(TODAY);
            assertThat(invoice.period()).isEqualTo(BillingPeriod.previousMonthOf(TODAY));
            assertThat(invoice.hours()).isEqualByComparingTo("37.5");
            assertThat(invoice.number()).isEqualTo("304");
        });
        assertThat(attachedTimesheets).singleElement().satisfies(timesheet -> {
            assertThat(timesheet.label()).isEqualTo("Tätigkeitsnachweis 10/2026");
            assertThat(timesheet.fileName()).isEqualTo("nachweis.pdf");
        });
        assertThat(console.transcript())
                .contains("Invoice date [13.11.2026]: ")
                .contains("Billing period [10/2026]: ")
                .contains("Invoice no. 304 to Beispiel GmbH")
                .contains("37,50 h × 95,00 €")
                .contains("4.239,38 €")
                .contains("Due date                 13.12.2026")
                .contains("✔ ⑥ PDF/A-3b checked")
                .contains("✔ ⑪ Invoice number incremented")
                .contains("Invoice created: /invoices/Rechnung 304.pdf");
        assertThat(console.allAnswersUsed()).isTrue();
    }

    @Test
    void acceptsOwnValues() {
        ScriptedConsole console = new ScriptedConsole("muster", "02.01.2027", "11/2026", "12.25", "nachweis.pdf", "y");

        assertThat(run(console)).isEqualTo(InteractiveDialog.CREATED);
        assertThat(generated).singleElement().satisfies(invoice -> {
            assertThat(invoice.customer().id()).isEqualTo("muster");
            assertThat(invoice.invoiceDate()).isEqualTo(LocalDate.of(2027, 1, 2));
            assertThat(invoice.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(invoice.period().to()).isEqualTo(LocalDate.of(2026, 11, 30));
            assertThat(invoice.hours()).isEqualByComparingTo("12.25");
            assertThat(invoice.dueDate()).isEqualTo(LocalDate.of(2027, 1, 16));
        });
        assertThat(console.transcript()).contains("Billing period [12/2026]: 11/2026"); // default follows the date
    }

    @Test
    void asksAgainAfterInvalidInput() {
        ScriptedConsole console = new ScriptedConsole(
                "7", "2",                      // no customer 7
                "31.02.2026", "1.12.2026",     // no 31 February
                "13/2026", "",                 // no month 13
                "abc", "0", "8",               // hours must be a positive number
                "fehlt.pdf", "nachweis.pdf",   // file does not exist
                "maybe", "yes");

        assertThat(run(console)).isEqualTo(InteractiveDialog.CREATED);
        assertThat(console.transcript())
                .contains("Please enter a number from 1 to 2 or a customer ID.")
                .contains("Please enter a date like 09.11.2026 (day.month.year).")
                .contains("Please enter a month like 10/2026.")
                .contains("Please enter a positive number, e.g. 37.5.")
                .contains("File not found: " + workDir.resolve("fehlt.pdf"))
                .contains("Please enter y or n.");
        assertThat(generated).singleElement().satisfies(invoice -> {
            assertThat(invoice.customer().id()).isEqualTo("muster");
            assertThat(invoice.period().from()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(invoice.hours()).isEqualByComparingTo("8");
        });
    }

    @Test
    void resolvesTildeInTheTimesheetPath() throws IOException {
        Files.createDirectories(homeDir.resolve("Downloads"));
        Files.writeString(homeDir.resolve("Downloads/okt.pdf"), "%PDF");

        assertThat(run(new ScriptedConsole("", "", "", "1", "~/Downloads/okt.pdf", ""))).isEqualTo(InteractiveDialog.CREATED);
        assertThat(attachedTimesheets).singleElement().extracting(Timesheet::fileName).isEqualTo("okt.pdf");
    }

    @Nested
    class Warnings {

        @Test
        void existingInvoiceForTheMonthAbortsByDefault() {
            existing = Optional.of("/invoices/2026-11-02 … Rechnung 302 für 2026-10.pdf");
            ScriptedConsole console = new ScriptedConsole("", "", "", "");

            assertThat(run(console)).isEqualTo(InteractiveDialog.ABORTED);
            assertThat(console.transcript())
                    .contains("⚠ There is already an invoice for Beispielkunde for 10/2026:")
                    .contains("Continue anyway? [y/N]: ")
                    .contains("Aborted, no invoice was created.");
            assertThat(generated).isEmpty();
        }

        @Test
        void existingInvoiceCanBeIgnored() {
            existing = Optional.of("/invoices/old.pdf");

            assertThat(run(new ScriptedConsole("", "", "", "y", "1", "nachweis.pdf", ""))).isEqualTo(InteractiveDialog.CREATED);
        }

        @Test
        void unsuitableTimesheetIsRejectedAndAskedAgain() throws IOException {
            Files.writeString(workDir.resolve("cmyk.pdf"), "%PDF cmyk");
            pdfAValidator = pdf -> new String(pdf).contains("cmyk")
                    ? ValidationResult.invalid("not PDF/A-3b compliant", List.of("rule 6.2.4.3-4: DeviceCMYK"))
                    : ValidationResult.valid("PDF/A-3b compliant");
            ScriptedConsole console = new ScriptedConsole("", "", "", "1", "cmyk.pdf", "nachweis.pdf", "");

            assertThat(run(console)).isEqualTo(InteractiveDialog.CREATED);
            assertThat(console.transcript())
                    .contains("✘ The timesheet cannot be used: not PDF/A-3b compliant")
                    .contains("  - rule 6.2.4.3-4: DeviceCMYK")
                    .contains("✔ Timesheet is suitable");
            assertThat(attachedTimesheets).singleElement().extracting(Timesheet::fileName).isEqualTo("nachweis.pdf");
        }
    }

    @Nested
    class Endings {

        @Test
        void declinedConfirmationGeneratesNothing() {
            ScriptedConsole console = new ScriptedConsole("", "", "", "1", "nachweis.pdf", "n");

            assertThat(run(console)).isEqualTo(InteractiveDialog.ABORTED);
            assertThat(generated).isEmpty();
            assertThat(nextNumber).isEqualTo(304);
        }

        @Test
        void abortWithCtrlCOrCtrlD() {
            ScriptedConsole console = new ScriptedConsole("", "");

            assertThat(run(console)).isEqualTo(InteractiveDialog.ABORTED);
            assertThat(console.transcript()).endsWith("Aborted, no invoice was created.");
        }

        @Test
        void failedGenerationShowsStepAndProblems() {
            eInvoiceResult = ValidationResult.invalid("invalid e-invoice", List.of("error: BR-CO-15 …"));
            ScriptedConsole console = new ScriptedConsole("", "", "", "1", "nachweis.pdf", "");

            assertThat(run(console)).isEqualTo(InteractiveDialog.FAILED);
            assertThat(console.transcript())
                    .contains("✔ ⑧ XML embedded")
                    .contains("✘ Error in step ⑨ invalid e-invoice")
                    .contains("  - error: BR-CO-15 …")
                    .contains("No invoice was stored; the invoice number is still free.")
                    .doesNotContain("⑩");
            assertThat(generated).isEmpty();
        }
    }
}
