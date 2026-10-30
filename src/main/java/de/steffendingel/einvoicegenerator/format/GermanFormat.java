package de.steffendingel.einvoicegenerator.format;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Formats amounts and dates the way they appear on German invoices, e.g. "3.562,50 €" and "02.11.2026".
 *
 * <p>Used by the template (step ②) and for the expected texts of the text check (step ④), so that both
 * use exactly the same format.
 */
public final class GermanFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.GERMANY);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy");

    private GermanFormat() {
    }

    /** "3.562,50 €" (two decimal places, half up). */
    public static String money(BigDecimal amount) {
        return decimal(amount) + " €";
    }

    /** "3.562,50" (two decimal places, half up), e.g. for hours. */
    public static String decimal(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        // DecimalFormat is not thread-safe, so create one per call
        DecimalFormat format = new DecimalFormat("#,##0.00", SYMBOLS);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** "19 %" (without decimal places if there are none, otherwise e.g. "7,5 %"). */
    public static String percent(BigDecimal rate) {
        Objects.requireNonNull(rate, "rate must not be null");
        return rate.stripTrailingZeros().toPlainString().replace('.', ',') + " %";
    }

    /** "02.11.2026" */
    public static String date(LocalDate date) {
        return DATE.format(Objects.requireNonNull(date, "date must not be null"));
    }

    /** "02.11.26" */
    public static String shortDate(LocalDate date) {
        return SHORT_DATE.format(Objects.requireNonNull(date, "date must not be null"));
    }
}
