package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void takesNameVersionAndUrlFromThePom() {
        assertThat(AppInfo.NAME).isEqualTo("E-Invoice Generator");
        assertThat(AppInfo.VERSION).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
        assertThat(AppInfo.URL).isEqualTo("https://github.com/sdingel/e-invoice-generator");
        assertThat(AppInfo.nameAndVersion()).isEqualTo("E-Invoice Generator " + AppInfo.VERSION);
    }
}
