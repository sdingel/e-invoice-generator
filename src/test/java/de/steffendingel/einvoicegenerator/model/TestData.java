package de.steffendingel.einvoicegenerator.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/** Example data for tests (obviously fictitious, see CLAUDE.md). */
public final class TestData {

    private TestData() {
    }

    public static Address sellerAddress() {
        return new Address("Musterstraße 1", "12345", "Musterstadt", "DE");
    }

    public static Seller seller() {
        return new Seller("Max Mustermann", sellerAddress(), "DE123456789", null,
                "DE89 3704 0044 0532 0130 00", "COBADEFFXXX", "max@example.com");
    }

    public static Customer customer() {
        return customer(new BigDecimal("95.00"), 30);
    }

    public static Customer customer(BigDecimal hourlyRate, int paymentTermDays) {
        return new Customer("beispiel", "Beispiel GmbH",
                new Address("Beispielweg 2", "54321", "Beispielstadt", "DE"),
                null, "buchhaltung@example.com", hourlyRate, paymentTermDays,
                "Beratungs- und Entwicklungsleistungen", Map.of("vertragsdatum", "15.01.2026"));
    }

    public static InvoiceRequest request(String hours) {
        return new InvoiceRequest(customer(), BillingPeriod.of(YearMonth.of(2026, 10)),
                new BigDecimal(hours), LocalDate.of(2026, 11, 2));
    }
}
