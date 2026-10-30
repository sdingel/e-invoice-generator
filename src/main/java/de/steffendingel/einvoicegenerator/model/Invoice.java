package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.requireNotNegative;
import static de.steffendingel.einvoicegenerator.model.Checks.requirePositive;
import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A fully calculated invoice with a single line item (hours × hourly rate). Basis for both the PDF and
 * the XML. Amounts are not formatted; monetary amounts have two decimal places.
 *
 * @param vatRate VAT rate in percent, e.g. 19
 */
public record Invoice(String number, LocalDate invoiceDate, LocalDate dueDate, BillingPeriod period,
                      Seller seller, Customer customer, BigDecimal hours, BigDecimal hourlyRate,
                      BigDecimal netAmount, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal grossAmount) {

    public Invoice {
        requireText(number, "number");
        Objects.requireNonNull(invoiceDate, "invoiceDate must not be null");
        Objects.requireNonNull(dueDate, "dueDate must not be null");
        if (dueDate.isBefore(invoiceDate)) {
            throw new IllegalArgumentException("dueDate must not be before invoiceDate");
        }
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(seller, "seller must not be null");
        Objects.requireNonNull(customer, "customer must not be null");
        requirePositive(hours, "hours");
        requirePositive(hourlyRate, "hourlyRate");
        requireNotNegative(netAmount, "netAmount");
        requireNotNegative(vatRate, "vatRate");
        requireNotNegative(vatAmount, "vatAmount");
        requireNotNegative(grossAmount, "grossAmount");
        if (netAmount.add(vatAmount).compareTo(grossAmount) != 0) {
            throw new IllegalArgumentException("grossAmount must equal netAmount + vatAmount");
        }
    }
}
