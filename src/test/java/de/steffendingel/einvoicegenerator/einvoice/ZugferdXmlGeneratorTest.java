package de.steffendingel.einvoicegenerator.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import de.steffendingel.einvoicegenerator.model.Address;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.Seller;
import de.steffendingel.einvoicegenerator.model.TestData;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Step ⑦: the XML is checked on its own, without a PDF. */
class ZugferdXmlGeneratorTest {

    static final byte[] TIMESHEET_PDF = "%PDF-1.7 example timesheet".getBytes();
    static final Timesheet TIMESHEET = new Timesheet("stundennachweis-2026-10.pdf", "Stundennachweis 10/2026", TIMESHEET_PDF);

    private static Xml xml;

    @BeforeAll
    static void generate() throws Exception {
        xml = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of(TIMESHEET)));
    }

    @Nested
    class Document {

        @Test
        void hasNumberTypeAndProfile() throws Exception {
            assertThat(xml.text("ExchangedDocument/ID")).isEqualTo("302");
            assertThat(xml.text("ExchangedDocument/TypeCode")).isEqualTo("380"); // commercial invoice
            assertThat(xml.text("GuidelineSpecifiedDocumentContextParameter/ID")).isEqualTo("urn:cen.eu:en16931:2017");
        }

        @Test
        void hasTheCorrectDates() throws Exception {
            assertThat(xml.text("ExchangedDocument/IssueDateTime/DateTimeString")).isEqualTo("20261102");
            assertThat(xml.text("BillingSpecifiedPeriod/StartDateTime/DateTimeString")).isEqualTo("20261001");
            assertThat(xml.text("BillingSpecifiedPeriod/EndDateTime/DateTimeString")).isEqualTo("20261031");
            assertThat(xml.text("ActualDeliverySupplyChainEvent/OccurrenceDateTime/DateTimeString")).isEqualTo("20261031");
            assertThat(xml.text("SpecifiedTradePaymentTerms/DueDateDateTime/DateTimeString")).isEqualTo("20261202");
            assertThat(xml.text("SpecifiedTradePaymentTerms/Description")).isEqualTo("Zahlbar bis 02.12.2026 ohne Abzug.");
        }

        @ParameterizedTest(name = "JVM time zone {0}")
        @ValueSource(strings = {"Europe/Berlin", "America/Los_Angeles", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC"})
        void datesDoNotDependOnTheTimeZone(String zone) throws Exception {
            assertThat(datesInTimeZone(zone)).containsExactly("20261102", "20261001", "20261031", "20261202");
        }

        @Test
        void datesSurviveATimeZoneChangeAtRuntime() throws Exception {
            // Mustang keeps the zone of its first use per thread; noon covers a difference of up to 12 hours
            TimeZone original = TimeZone.getDefault();
            try {
                new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of()); // formatter created in this thread
                TimeZone.setDefault(TimeZone.getTimeZone(original.getRawOffset() > 0 ? "America/New_York" : "Europe/Berlin"));
                Xml changed = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of()));

                assertThat(changed.text("ExchangedDocument/IssueDateTime/DateTimeString")).isEqualTo("20261102");
            } finally {
                TimeZone.setDefault(original);
            }
        }

        /**
         * Generates the XML in a new thread after switching the default time zone, like a JVM that was
         * started in that zone (Mustang creates its date formatter per thread).
         */
        private static List<String> datesInTimeZone(String zone) throws Exception {
            TimeZone original = TimeZone.getDefault();
            AtomicReference<Object> result = new AtomicReference<>();
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                Thread thread = new Thread(() -> {
                    try {
                        Xml inZone = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of()));
                        result.set(List.of(
                                inZone.text("ExchangedDocument/IssueDateTime/DateTimeString"),
                                inZone.text("BillingSpecifiedPeriod/StartDateTime/DateTimeString"),
                                inZone.text("BillingSpecifiedPeriod/EndDateTime/DateTimeString"),
                                inZone.text("SpecifiedTradePaymentTerms/DueDateDateTime/DateTimeString")));
                    } catch (Exception e) {
                        result.set(e);
                    }
                });
                thread.start();
                thread.join();
            } finally {
                TimeZone.setDefault(original);
            }
            if (result.get() instanceof Exception e) {
                throw e;
            }
            @SuppressWarnings("unchecked")
            List<String> dates = (List<String>) result.get();
            return dates;
        }
    }

    @Nested
    class Amounts {

        @Test
        void matchTheCalculatedInvoice() throws Exception {
            assertThat(xml.text("BilledQuantity")).startsWith("37.5");
            assertThat(xml.attribute("BilledQuantity", "unitCode")).isEqualTo("HUR");
            assertThat(xml.text("NetPriceProductTradePrice/ChargeAmount")).startsWith("95.00");
            assertThat(xml.text("SpecifiedTradeSettlementHeaderMonetarySummation/TaxBasisTotalAmount")).isEqualTo("3562.50");
            assertThat(xml.text("SpecifiedTradeSettlementHeaderMonetarySummation/TaxTotalAmount")).isEqualTo("676.88");
            assertThat(xml.text("SpecifiedTradeSettlementHeaderMonetarySummation/GrandTotalAmount")).isEqualTo("4239.38");
            assertThat(xml.text("SpecifiedTradeSettlementHeaderMonetarySummation/DuePayableAmount")).isEqualTo("4239.38");
        }

        @Test
        void useStandardVatRate() throws Exception {
            assertThat(xml.text("ApplicableTradeTax/CategoryCode")).isEqualTo("S");
            assertThat(xml.text("ApplicableTradeTax/RateApplicablePercent")).startsWith("19");
        }

        @Test
        void containTheLineItemText() throws Exception {
            assertThat(xml.text("SpecifiedTradeProduct/Name")).isEqualTo("Beratungs- und Entwicklungsleistungen");
        }
    }

    @Nested
    class Parties {

        @Test
        void sellerHasAddressVatIdEmailAndBankAccount() throws Exception {
            assertThat(xml.text("SellerTradeParty/Name")).isEqualTo("Max Mustermann");
            assertThat(xml.text("SellerTradeParty/PostalTradeAddress/PostcodeCode")).isEqualTo("12345");
            assertThat(xml.text("SellerTradeParty/PostalTradeAddress/LineOne")).isEqualTo("Musterstraße 1");
            assertThat(xml.text("SellerTradeParty/URIID")).isEqualTo("max@example.com");
            assertThat(xml.text("SellerTradeParty/SpecifiedTaxRegistration/ID")).isEqualTo("DE123456789");
            assertThat(xml.attribute("SellerTradeParty/SpecifiedTaxRegistration/ID", "schemeID")).isEqualTo("VA");
            assertThat(xml.text("SpecifiedTradeSettlementPaymentMeans/TypeCode")).isEqualTo("58"); // SEPA credit transfer
            assertThat(xml.text("IBANID")).isEqualTo("DE89370400440532013000");
            assertThat(xml.text("BICID")).isEqualTo("COBADEFFXXX");
        }

        @Test
        void sellerWithTaxNumberInsteadOfVatId() throws Exception {
            Seller seller = new Seller("Max Mustermann", TestData.sellerAddress(), null, "123/456/78901",
                    "DE89370400440532013000", null, "max@example.com");
            Invoice example = TestData.invoice();
            Invoice invoice = new Invoice(example.number(), example.invoiceDate(), example.dueDate(),
                    example.period(), seller, example.customer(), example.hours(), example.hourlyRate(),
                    example.netAmount(), example.vatRate(), example.vatAmount(), example.grossAmount());

            Xml withTaxNumber = new Xml(new ZugferdXmlGenerator().generateXml(invoice, List.of()));

            assertThat(withTaxNumber.text("SellerTradeParty/SpecifiedTaxRegistration/ID")).isEqualTo("123/456/78901");
            assertThat(withTaxNumber.attribute("SellerTradeParty/SpecifiedTaxRegistration/ID", "schemeID")).isEqualTo("FC");
            assertThat(withTaxNumber.count("BICID")).isZero();
        }

        @Test
        void buyerHasNameAndAddress() throws Exception {
            assertThat(xml.text("BuyerTradeParty/Name")).isEqualTo("Beispiel GmbH");
            assertThat(xml.text("BuyerTradeParty/PostalTradeAddress/PostcodeCode")).isEqualTo("54321");
            assertThat(xml.text("BuyerTradeParty/PostalTradeAddress/CityName")).isEqualTo("Beispielstadt");
            assertThat(xml.text("BuyerTradeParty/URIID")).isEqualTo("buchhaltung@example.com");
        }

        @Test
        void buyerVatIdIsIncludedIfConfigured() throws Exception {
            Customer example = TestData.customer();
            Customer customer = new Customer(example.id(), example.name(), example.address(), "DE987654321",
                    null, example.hourlyRate(), example.paymentTermDays(), example.itemText(), Map.of());

            Xml withVatId = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(customer), List.of()));

            assertThat(withVatId.text("BuyerTradeParty/SpecifiedTaxRegistration/ID")).isEqualTo("DE987654321");
            assertThat(withVatId.count("BuyerTradeParty/URIID")).isZero();
        }

        @Test
        void keepsSpecialCharacters() throws Exception {
            Customer example = TestData.customer();
            Customer customer = new Customer(example.id(), "Müller & Söhne <GmbH>",
                    new Address("Weg \"A\" 1", "01067", "Dresden", "DE"), null, null, example.hourlyRate(),
                    example.paymentTermDays(), example.itemText(), Map.of());

            Xml special = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(customer), List.of()));

            assertThat(special.text("BuyerTradeParty/Name")).isEqualTo("Müller & Söhne <GmbH>");
            assertThat(special.text("BuyerTradeParty/PostalTradeAddress/PostcodeCode")).isEqualTo("01067");
        }
    }

    @Nested
    class Timesheets {

        @Test
        void areEmbeddedAsSupportingDocuments() throws Exception {
            assertThat(xml.count("AdditionalReferencedDocument")).isEqualTo(1);
            assertThat(xml.text("AdditionalReferencedDocument/TypeCode")).isEqualTo("916");
            assertThat(xml.text("AdditionalReferencedDocument/Name")).isEqualTo("Stundennachweis 10/2026");
            assertThat(xml.attribute("AttachmentBinaryObject", "filename")).isEqualTo("stundennachweis-2026-10.pdf");
            assertThat(xml.attribute("AttachmentBinaryObject", "mimeCode")).isEqualTo("application/pdf");
            assertThat(Base64.getMimeDecoder().decode(xml.text("AttachmentBinaryObject"))).isEqualTo(TIMESHEET_PDF);
        }

        @Test
        void areOptional() throws Exception {
            Xml without = new Xml(new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of()));

            assertThat(without.count("AdditionalReferencedDocument")).isZero();
        }
    }

    @Test
    void xmlAloneIsValidWithoutWarnings() throws Exception {
        byte[] generated = new ZugferdXmlGenerator().generateXml(TestData.invoice(), List.of(TIMESHEET));

        ValidationResult result = new MustangValidator().validate(generated);

        assertThat(result.problems()).isEmpty();
        assertThat(result.valid()).isTrue();
    }
}
