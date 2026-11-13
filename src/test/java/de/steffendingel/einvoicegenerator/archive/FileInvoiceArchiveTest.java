package de.steffendingel.einvoicegenerator.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.InvoiceCalculator;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileInvoiceArchiveTest {

    private static final byte[] CONTENT = "%PDF-1.7 e-invoice".getBytes();
    private static final String FILE_NAME = "2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf";

    @TempDir
    Path tempDir;

    private FileInvoiceArchive archive() {
        return new FileInvoiceArchive(tempDir.resolve("rechnungen"), null);
    }

    @Nested
    class Store {

        @Test
        void writesTheFileUnderTheTemplateName() throws IOException {
            String stored = archive().store(TestData.invoice(), CONTENT);

            Path file = tempDir.resolve("rechnungen").resolve(FILE_NAME);
            assertThat(stored).isEqualTo(file.toString());
            assertThat(file).hasBinaryContent(CONTENT);
        }

        @Test
        void neverOverwritesAnExistingInvoice() throws IOException {
            archive().store(TestData.invoice(), CONTENT);

            assertThatThrownBy(() -> archive().store(TestData.invoice(), "other".getBytes()))
                    .isInstanceOf(FileAlreadyExistsException.class)
                    .hasMessageContaining("not overwritten");
            assertThat(tempDir.resolve("rechnungen").resolve(FILE_NAME)).hasBinaryContent(CONTENT);
        }

        @Test
        void leavesNoTemporaryFiles() throws IOException {
            archive().store(TestData.invoice(), CONTENT);

            try (Stream<Path> files = Files.list(tempDir.resolve("rechnungen"))) {
                assertThat(files).extracting(file -> file.getFileName().toString()).containsExactly(FILE_NAME);
            }
        }

        @Test
        void rejectsAnInvalidTemplateRightAway() {
            assertThatIllegalArgumentException().isThrownBy(() -> new FileInvoiceArchive(tempDir, "{datum}.pdf"));
        }
    }

    @Nested
    class FindExisting {

        @Test
        void findsNothingWithoutDirectoryOrInvoices() throws IOException {
            assertThat(archive().findExisting(TestData.customer(), TestData.invoice().period())).isEmpty();
            Files.createDirectories(tempDir.resolve("rechnungen"));
            assertThat(archive().findExisting(TestData.customer(), TestData.invoice().period())).isEmpty();
        }

        @Test
        void findsAnInvoiceForTheSameCustomerAndPeriod() throws IOException {
            String stored = archive().store(TestData.invoice(), CONTENT);

            assertThat(archive().findExisting(TestData.customer(), BillingPeriod.of(YearMonth.of(2026, 10))))
                    .contains(stored);
        }

        @Test
        void ignoresOtherPeriodsAndCustomers() throws IOException {
            archive().store(TestData.invoice(), CONTENT);
            var other = TestData.customer();
            var otherCustomer = new de.steffendingel.einvoicegenerator.model.Customer("muster", "Muster AG", null,
                    other.address(), null, null, other.hourlyRate(), 14, other.itemText(), null);

            assertThat(archive().findExisting(TestData.customer(), BillingPeriod.of(YearMonth.of(2026, 9)))).isEmpty();
            assertThat(archive().findExisting(otherCustomer, BillingPeriod.of(YearMonth.of(2026, 10)))).isEmpty();
        }

        @Test
        void findsItAlsoWithAnotherNumberAndDate() throws IOException {
            InvoiceRequest later = new InvoiceRequest(TestData.customer(), BillingPeriod.of(YearMonth.of(2026, 10)),
                    BigDecimal.ONE, LocalDate.of(2026, 12, 1));
            Invoice correction = new InvoiceCalculator(TestData.seller()).calculate(later, "315");
            String stored = archive().store(correction, CONTENT);

            assertThat(archive().findExisting(TestData.customer(), BillingPeriod.of(YearMonth.of(2026, 10))))
                    .contains(stored);
        }
    }
}
