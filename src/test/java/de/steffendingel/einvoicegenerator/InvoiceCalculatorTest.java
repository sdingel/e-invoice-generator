package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InvoiceCalculatorTest {

    private final InvoiceCalculator calculator = new InvoiceCalculator(TestData.seller());

    @Test
    void calculatesTheExampleFromTheProofOfConcept() {
        Invoice invoice = calculator.calculate(TestData.request("37.5"), "302");

        assertThat(invoice.number()).isEqualTo("302");
        assertThat(invoice.netAmount()).isEqualByComparingTo("3562.50");
        assertThat(invoice.vatRate()).isEqualByComparingTo("19");
        assertThat(invoice.vatAmount()).isEqualByComparingTo("676.88"); // 676.875 rounded half up
        assertThat(invoice.grossAmount()).isEqualByComparingTo("4239.38");
    }

    @Test
    void amountsHaveTwoDecimalPlaces() {
        Invoice invoice = calculator.calculate(TestData.request("37.5"), "302");

        assertThat(invoice.netAmount().scale()).isEqualTo(2);
        assertThat(invoice.vatAmount().scale()).isEqualTo(2);
        assertThat(invoice.grossAmount().scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} h × {1} € = {2} € net, {3} € VAT")
    @CsvSource({
            // hours, rate,  net,     VAT
            "0.25,   95.00,  23.75,   4.51",   // VAT 4.5125
            "0.333,  95.00,  31.64,   6.01",   // net 31.635 rounded half up
            "1.5,    101.00, 151.50,  28.79",  // VAT 28.785: half up, not half even (28.78)
            "160,    120.00, 19200.00, 3648.00",
            "1,      0.01,   0.01,    0.00",   // VAT 0.0019 rounds to zero
    })
    void roundsHalfUpToCents(String hours, String rate, String net, String vat) {
        InvoiceRequest request = new InvoiceRequest(TestData.customer(new BigDecimal(rate), 30),
                BillingPeriod.of(YearMonth.of(2026, 10)), new BigDecimal(hours), LocalDate.of(2026, 11, 2));

        Invoice invoice = calculator.calculate(request, "1");

        assertThat(invoice.netAmount()).isEqualByComparingTo(net);
        assertThat(invoice.vatAmount()).isEqualByComparingTo(vat);
        assertThat(invoice.grossAmount()).isEqualByComparingTo(new BigDecimal(net).add(new BigDecimal(vat)));
    }

    @ParameterizedTest(name = "{0} + {1} days = {2}")
    @CsvSource({
            "2026-11-02, 30, 2026-12-02",
            "2026-12-15, 30, 2027-01-14",  // year change
            "2028-02-15, 14, 2028-02-29",  // leap year
            "2026-11-02,  0, 2026-11-02",  // due immediately
            "2026-11-06, 15, 2026-11-21",  // Saturday: not moved to the next working day
    })
    void dueDateIsInvoiceDatePlusPaymentTerm(LocalDate invoiceDate, int days, LocalDate expectedDueDate) {
        InvoiceRequest request = new InvoiceRequest(TestData.customer(new BigDecimal("95.00"), days),
                BillingPeriod.previousMonthOf(invoiceDate), BigDecimal.ONE, invoiceDate);

        assertThat(calculator.calculate(request, "1").dueDate()).isEqualTo(expectedDueDate);
    }

    @Test
    void takesOverRequestDataUnchanged() {
        InvoiceRequest request = TestData.request("37.5");

        Invoice invoice = calculator.calculate(request, "302");

        assertThat(invoice.invoiceDate()).isEqualTo(request.invoiceDate());
        assertThat(invoice.period()).isEqualTo(request.period());
        assertThat(invoice.customer()).isEqualTo(request.customer());
        assertThat(invoice.seller()).isEqualTo(TestData.seller());
        assertThat(invoice.hours()).isEqualByComparingTo("37.5");
        assertThat(invoice.hourlyRate()).isEqualByComparingTo("95.00");
    }

    @Test
    void requiresSellerAndRequest() {
        assertThatNullPointerException().isThrownBy(() -> new InvoiceCalculator(null));
        assertThatNullPointerException().isThrownBy(() -> calculator.calculate(null, "1"));
    }
}
