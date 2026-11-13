package de.steffendingel.einvoicegenerator.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.steffendingel.einvoicegenerator.InvoiceCalculator;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

class FileNameTemplateTest {

    @Test
    void defaultMatchesTheAgreedFormat() {
        assertThat(new FileNameTemplate(null).fileName(TestData.invoice()))
                .isEqualTo("2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf");
    }

    @Test
    void canBeChangedForOtherLanguages() {
        FileNameTemplate english = new FileNameTemplate("{invoiceDate} {customerName} Invoice {number} for {period}");

        assertThat(english.fileName(TestData.invoice())).isEqualTo("2026-11-02 Beispiel GmbH Invoice 302 for 2026-10.pdf");
    }

    @Test
    void usesTheNameIfThereIsNoShortName() {
        Customer customer = withName(TestData.customer(), "Beispiel GmbH", null);

        assertThat(new FileNameTemplate("{customerShortName} {number}").fileName(TestData.invoice(customer)))
                .isEqualTo("Beispiel GmbH 302.pdf");
    }

    @Test
    void replacesCharactersThatAreNotAllowedInFileNames() {
        Customer customer = withName(TestData.customer(), "A/B: \"C\" <D>", null);

        assertThat(new FileNameTemplate("{customerShortName} {number}").fileName(TestData.invoice(customer)))
                .isEqualTo("A-B- -C- -D- 302.pdf");
    }

    @Test
    void takesTheMonthInWhichTheBillingPeriodStarts() {
        InvoiceRequest request = new InvoiceRequest(TestData.customer(),
                new BillingPeriod(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 14)),
                BigDecimal.TEN, LocalDate.of(2026, 10, 20));
        Invoice invoice = new InvoiceCalculator(TestData.seller()).calculate(request, "303");

        assertThat(new FileNameTemplate("{number} {period}").fileName(invoice)).isEqualTo("303 2026-09.pdf");
    }

    @Test
    void rejectsUnknownPlaceholders() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileNameTemplate("{number} {customer} {datum}"))
                .withMessageContaining("{customer}, {datum}")
                .withMessageContaining("known: {customerId}");
    }

    @Test
    void requiresTheInvoiceNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileNameTemplate("{invoiceDate} {customerShortName}"))
                .withMessageContaining("{number}");
    }

    @Test
    void patternFindsOtherInvoicesOfTheSameCustomerAndPeriod() {
        FileNameTemplate template = new FileNameTemplate(null);
        var pattern = template.patternFor(TestData.customer(), TestData.invoice().period());

        assertThat(pattern.matcher("2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf").matches()).isTrue();
        assertThat(pattern.matcher("2026-12-01 Max Mustermann Beispielkunde Rechnung 310 für 2026-10.pdf").matches()).isTrue();
        assertThat(pattern.matcher("2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-09.pdf").matches()).isFalse();
        assertThat(pattern.matcher("2026-11-02 Max Mustermann Anderer Kunde Rechnung 302 für 2026-10.pdf").matches()).isFalse();
    }

    private static Customer withName(Customer c, String name, String shortName) {
        return new Customer(c.id(), name, shortName, c.address(), c.vatId(), c.email(), c.hourlyRate(),
                c.paymentTermDays(), c.itemText(), Map.of());
    }
}
