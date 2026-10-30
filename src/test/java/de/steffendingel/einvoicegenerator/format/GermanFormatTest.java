package de.steffendingel.einvoicegenerator.format;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GermanFormatTest {

    @ParameterizedTest(name = "{0} → \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "3562.5     | 3.562,50 €",
            "4239.38    | 4.239,38 €",
            "0          | 0,00 €",
            "0.285      | 0,29 €",          // half up
            "1234567.89 | 1.234.567,89 €",
    })
    void formatsMoney(BigDecimal amount, String expected) {
        assertThat(GermanFormat.money(amount)).isEqualTo(expected);
    }

    @Test
    void formatsDecimalsWithTwoPlaces() {
        assertThat(GermanFormat.decimal(new BigDecimal("37.5"))).isEqualTo("37,50");
        assertThat(GermanFormat.decimal(new BigDecimal("1.5"))).isEqualTo("1,50");
    }

    @Test
    void formatsPercentWithoutUnnecessaryDecimals() {
        assertThat(GermanFormat.percent(new BigDecimal("19"))).isEqualTo("19 %");
        assertThat(GermanFormat.percent(new BigDecimal("19.00"))).isEqualTo("19 %");
        assertThat(GermanFormat.percent(new BigDecimal("10"))).isEqualTo("10 %");
        assertThat(GermanFormat.percent(new BigDecimal("7.5"))).isEqualTo("7,5 %");
    }

    @Test
    void formatsIbanInGroupsOfFour() {
        assertThat(GermanFormat.iban("DE89370400440532013000")).isEqualTo("DE89 3704 0044 0532 0130 00");
        assertThat(GermanFormat.iban("DE89 3704 0044 0532 0130 00")).isEqualTo("DE89 3704 0044 0532 0130 00");
        assertThat(GermanFormat.iban("AT611904300234573201")).isEqualTo("AT61 1904 3002 3457 3201");
    }

    @Test
    void formatsDates() {
        LocalDate date = LocalDate.of(2026, 11, 2);

        assertThat(GermanFormat.date(date)).isEqualTo("02.11.2026");
        assertThat(GermanFormat.shortDate(date)).isEqualTo("02.11.26");
    }
}
