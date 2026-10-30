package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.requirePositive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Input from the dialog that an invoice is calculated from.
 *
 * @param hours worked hours, decimals allowed (e.g. 37.5)
 */
public record InvoiceRequest(Customer customer, BillingPeriod period, BigDecimal hours, LocalDate invoiceDate) {

    public InvoiceRequest {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(period, "period must not be null");
        requirePositive(hours, "hours");
        Objects.requireNonNull(invoiceDate, "invoiceDate must not be null");
    }
}
