package de.steffendingel.einvoicegenerator.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Service period of an invoice (both days inclusive).
 */
public record BillingPeriod(LocalDate from, LocalDate to) {

    public BillingPeriod {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to: " + from + " > " + to);
        }
    }

    /** The whole month. */
    public static BillingPeriod of(YearMonth month) {
        return new BillingPeriod(month.atDay(1), month.atEndOfMonth());
    }

    /** Default period: the whole month before the invoice date (January → December of the previous year). */
    public static BillingPeriod previousMonthOf(LocalDate invoiceDate) {
        return of(YearMonth.from(invoiceDate).minusMonths(1));
    }
}
