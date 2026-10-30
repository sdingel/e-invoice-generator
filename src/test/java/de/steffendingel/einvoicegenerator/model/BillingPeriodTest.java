package de.steffendingel.einvoicegenerator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingPeriodTest {

    @ParameterizedTest(name = "{0} → {1} to {2}")
    @CsvSource({
            "2026-10, 2026-10-01, 2026-10-31",
            "2026-02, 2026-02-01, 2026-02-28",
            "2028-02, 2028-02-01, 2028-02-29",  // leap year
    })
    void coversTheWholeMonth(YearMonth month, LocalDate from, LocalDate to) {
        assertThat(BillingPeriod.of(month)).isEqualTo(new BillingPeriod(from, to));
    }

    @ParameterizedTest(name = "invoice date {0} → {1}")
    @CsvSource({
            "2026-11-02, 2026-10",
            "2026-11-30, 2026-10",
            "2027-01-05, 2026-12",  // January → December of the previous year
            "2026-03-31, 2026-02",
    })
    void defaultsToThePreviousMonth(LocalDate invoiceDate, YearMonth expectedMonth) {
        assertThat(BillingPeriod.previousMonthOf(invoiceDate)).isEqualTo(BillingPeriod.of(expectedMonth));
    }

    @Test
    void allowsASingleDayButNotAReversedPeriod() {
        LocalDate day = LocalDate.of(2026, 10, 15);

        assertThat(new BillingPeriod(day, day).from()).isEqualTo(day);
        assertThatIllegalArgumentException().isThrownBy(() -> new BillingPeriod(day, day.minusDays(1)));
    }
}
