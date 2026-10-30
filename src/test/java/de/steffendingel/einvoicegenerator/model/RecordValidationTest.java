package de.steffendingel.einvoicegenerator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Checks the argument validation and normalization in the constructors of the model records. */
class RecordValidationTest {

    @Nested
    class AddressTest {

        @Test
        void keepsZipAsTextWithLeadingZero() {
            assertThat(new Address("Straße 1", "01067", "Dresden", "DE").zip()).isEqualTo("01067");
        }

        @Test
        void requiresTwoLetterUpperCaseCountryCode() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Address("Straße 1", "12345", "Ort", "de"));
            assertThatIllegalArgumentException().isThrownBy(() -> new Address("Straße 1", "12345", "Ort", "DEU"));
        }

        @Test
        void rejectsBlankFields() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Address(" ", "12345", "Ort", "DE"));
            assertThatNullPointerException().isThrownBy(() -> new Address("Straße 1", null, "Ort", "DE"));
        }
    }

    @Nested
    class SellerTest {

        @Test
        void requiresVatIdOrTaxNumber() {
            Address address = TestData.sellerAddress();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new Seller("Max", address, null, " ", "DE89370400440532013000", null, "m@example.com"))
                    .withMessageContaining("vatId or taxNumber");
            assertThat(new Seller("Max", address, null, "123/456/78901", "DE89370400440532013000", null, "m@example.com")
                    .taxNumber()).isEqualTo("123/456/78901");
        }

        @Test
        void normalizesIbanAndBlankOptionalValues() {
            Seller seller = new Seller("Max", TestData.sellerAddress(), "DE123456789", "",
                    "DE89 3704 0044 0532 0130 00", " ", "m@example.com");

            assertThat(seller.iban()).isEqualTo("DE89370400440532013000");
            assertThat(seller.taxNumber()).isNull();
            assertThat(seller.bic()).isNull();
        }
    }

    @Nested
    class CustomerTest {

        @Test
        void fieldsAreNeverNullAndCannotBeChangedFromOutside() {
            Map<String, String> fields = new HashMap<>(Map.of("vertragsdatum", "15.01.2026"));
            Customer customer = customerWithFields(fields);
            fields.put("ansprechpartner", "z. Hd. Erika Musterfrau");

            assertThat(customer.fields()).containsOnlyKeys("vertragsdatum");
            assertThatThrownBy(() -> customer.fields().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
            assertThat(customerWithFields(null).fields()).isEmpty();
        }

        @Test
        void rejectsInvalidRateAndPaymentTerm() {
            assertThatIllegalArgumentException().isThrownBy(() -> TestData.customer(BigDecimal.ZERO, 30));
            assertThatIllegalArgumentException().isThrownBy(() -> TestData.customer(new BigDecimal("95"), -1));
        }

        private Customer customerWithFields(Map<String, String> fields) {
            Customer c = TestData.customer();
            return new Customer(c.id(), c.name(), c.address(), c.vatId(), c.email(), c.hourlyRate(),
                    c.paymentTermDays(), c.itemText(), fields);
        }
    }

    @Nested
    class InvoiceRequestTest {

        @Test
        void requiresPositiveHours() {
            assertThatIllegalArgumentException().isThrownBy(() -> TestData.request("0"));
            assertThatIllegalArgumentException().isThrownBy(() -> TestData.request("-1"));
        }
    }

    @Nested
    class InvoiceTest {

        @Test
        void rejectsInconsistentAmountsAndDates() {
            LocalDate date = LocalDate.of(2026, 11, 2);
            BillingPeriod period = BillingPeriod.of(YearMonth.of(2026, 10));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> invoice(date, date, period, "100.00", "19.00", "119.01"))
                    .withMessageContaining("grossAmount");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> invoice(date, date.minusDays(1), period, "100.00", "19.00", "119.00"))
                    .withMessageContaining("dueDate");
        }

        private Invoice invoice(LocalDate invoiceDate, LocalDate dueDate, BillingPeriod period,
                                String net, String vat, String gross) {
            return new Invoice("1", invoiceDate, dueDate, period, TestData.seller(), TestData.customer(),
                    BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal(net), new BigDecimal("19"),
                    new BigDecimal(vat), new BigDecimal(gross));
        }
    }

    @Nested
    class TimesheetTest {

        @Test
        void isImmutableAndComparedByContent() {
            byte[] content = {1, 2, 3};
            Timesheet timesheet = new Timesheet("timesheet.pdf", "Stundennachweis 10/2026", content);
            content[0] = 9;
            timesheet.pdf()[1] = 9;

            assertThat(timesheet.pdf()).containsExactly(1, 2, 3);
            assertThat(timesheet).isEqualTo(new Timesheet("timesheet.pdf", "Stundennachweis 10/2026", new byte[] {1, 2, 3}));
            assertThat(timesheet).hasSameHashCodeAs(new Timesheet("timesheet.pdf", "Stundennachweis 10/2026", new byte[] {1, 2, 3}));
            assertThat(timesheet.toString()).contains("3 bytes");
        }

        @Test
        void rejectsEmptyPdf() {
            assertThatIllegalArgumentException().isThrownBy(() -> new Timesheet("t.pdf", "Nachweis", new byte[0]));
        }
    }

    @Nested
    class ValidationResultTest {

        @Test
        void validResultHasNoProblems() {
            assertThat(ValidationResult.valid("PDF/A-3b compliant").problems()).isEmpty();
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ValidationResult(true, "ok", List.of("problem")));
        }

        @Test
        void invalidResultKeepsProblems() {
            ValidationResult result = ValidationResult.invalid("not PDF/A", List.of("6.2.4.3-2: DeviceRGB"));

            assertThat(result.valid()).isFalse();
            assertThat(result.problems()).containsExactly("6.2.4.3-2: DeviceRGB");
        }
    }
}
