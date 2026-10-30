package de.steffendingel.einvoicegenerator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Seller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    private static final Path EXAMPLES = Path.of("examples", "config");

    @TempDir
    Path home;

    @TempDir
    Path configDir;

    private ConfigLoader loader() {
        return new ConfigLoader(home);
    }

    private Path write(String fileName, String yaml) throws IOException {
        return Files.writeString(configDir.resolve(fileName), yaml);
    }

    private static final String SELLER = """
            seller:
              name: Max Mustermann
              address: { street: Musterstraße 1, zip: "12345", city: Musterstadt, country: DE }
              vatId: DE123456789
              iban: DE89370400440532013000
              email: max@example.com
            """;

    private static final String CUSTOMER = """
            customers:
              - id: beispiel
                name: Beispiel GmbH
                address: { street: Beispielweg 2, zip: "54321", city: Beispielstadt, country: DE }
                hourlyRate: 95.00
                paymentTermDays: 30
                itemText: Beratung
            """;

    @Nested
    class Examples {

        @Test
        void exampleSettingsAreValid() throws Exception {
            Settings settings = loader().loadSettings(EXAMPLES.resolve("settings.yaml"));

            Seller seller = settings.seller();
            assertThat(seller.name()).isEqualTo("Max Mustermann");
            assertThat(seller.address().zip()).isEqualTo("12345");
            assertThat(seller.vatId()).isEqualTo("DE123456789");
            assertThat(seller.taxNumber()).isNull();
            assertThat(seller.iban()).isEqualTo("DE89370400440532013000");
            assertThat(settings.outputDir()).isEqualTo(home.resolve("rechnungen").toAbsolutePath());
            assertThat(settings.logoFile()).isEmpty();
            assertThat(settings.templateFile()).isEmpty();
        }

        @Test
        void exampleCustomersAreValid() throws Exception {
            List<Customer> customers = loader().loadCustomers(EXAMPLES.resolve("customers.yaml"));

            assertThat(customers).extracting(Customer::id).containsExactly("beispiel", "muster");
            Customer first = customers.get(0);
            assertThat(first.hourlyRate()).isEqualByComparingTo("95.00");
            assertThat(first.paymentTermDays()).isEqualTo(30);
            assertThat(first.fields()).containsEntry("vertragsdatum", "15.01.2026")
                    .containsEntry("ansprechpartner", "z. Hd. Erika Musterfrau");
            Customer second = customers.get(1);
            assertThat(second.address().zip()).isEqualTo("01067");
            assertThat(second.hourlyRate()).isEqualByComparingTo("110");
            assertThat(second.vatId()).isNull();
            assertThat(second.fields()).isEmpty();
        }
    }

    @Nested
    class Paths {

        @Test
        void resolvesRelativePathsAgainstTheSettingsFile() throws Exception {
            Files.writeString(configDir.resolve("logo.svg"), "<svg/>");
            Path file = write("settings.yaml", SELLER + "outputDir: out\nlogo: logo.svg\n");

            Settings settings = loader().loadSettings(file);

            assertThat(settings.outputDir()).isEqualTo(configDir.resolve("out").toAbsolutePath());
            assertThat(settings.logoFile()).contains(configDir.resolve("logo.svg").toAbsolutePath());
        }

        @Test
        void resolvesTildeToTheHomeDirectory() throws Exception {
            Files.createDirectories(home.resolve("config"));
            Files.writeString(home.resolve("config/invoice.ftlh"), "<html/>");
            // A bare "~" would mean null in YAML, so "~/" is used for the home directory itself
            Path file = write("settings.yaml", SELLER + "outputDir: ~/\ntemplate: ~/config/invoice.ftlh\n");

            Settings settings = loader().loadSettings(file);

            assertThat(settings.outputDir()).isEqualTo(home.toAbsolutePath());
            assertThat(settings.templateFile()).contains(home.resolve("config/invoice.ftlh").toAbsolutePath());
        }

        @Test
        void reportsAMissingLogoOrTemplate() throws IOException {
            Path file = write("settings.yaml", SELLER + "outputDir: out\nlogo: missing.svg\n");

            assertThatThrownBy(() -> loader().loadSettings(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("logo not found")
                    .hasMessageContaining("missing.svg");
        }

        @Test
        void requiresTheOutputDirectory() throws IOException {
            Path file = write("settings.yaml", SELLER);

            assertThatThrownBy(() -> loader().loadSettings(file)).hasMessageContaining("\"outputDir\" is missing");
        }
    }

    @Nested
    class Errors {

        @Test
        void reportsTyposInRequiredKeys() throws IOException {
            Path file = write("customers.yaml", CUSTOMER.replace("hourlyRate:", "hourlyRat:"));

            assertThatThrownBy(() -> loader().loadCustomers(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("customers.yaml")
                    .hasMessageContaining("customers[0].hourlyRat: unknown key \"hourlyRat\"")
                    .hasMessageContaining("allowed: id, name, address");
        }

        @Test
        void reportsTyposInOptionalAndNestedKeys() throws IOException {
            Path optional = write("optional.yaml", CUSTOMER + "    emial: x@example.com\n");
            Path nested = write("nested.yaml", SELLER.replace("city:", "cty:") + "outputDir: out\n");

            assertThatThrownBy(() -> loader().loadCustomers(optional)).hasMessageContaining("customers[0].emial: unknown key");
            assertThatThrownBy(() -> loader().loadSettings(nested)).hasMessageContaining("seller.address.cty: unknown key");
        }

        @Test
        void allowsAnyKeyInTheFreeFields() throws Exception {
            Path file = write("customers.yaml", CUSTOMER + "    fields:\n      irgendwas: \"Wert\"\n");

            assertThat(loader().loadCustomers(file).get(0).fields()).containsEntry("irgendwas", "Wert");
        }

        @Test
        void doesNotTurnAMissingPaymentTermIntoZero() throws IOException {
            Path file = write("customers.yaml", CUSTOMER.replace("    paymentTermDays: 30\n", ""));

            assertThatThrownBy(() -> loader().loadCustomers(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("paymentTermDays");
        }

        @Test
        void requiresQuotedZipCodes() throws IOException {
            Path file = write("customers.yaml", CUSTOMER.replace("zip: \"54321\"", "zip: 54321"));

            assertThatThrownBy(() -> loader().loadCustomers(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("customers[0].address.zip: text must be in quotes");
        }

        @Test
        void passesOnValidationErrorsOfTheModel() throws IOException {
            Path file = write("customers.yaml", CUSTOMER.replace("hourlyRate: 95.00", "hourlyRate: 0"));

            assertThatThrownBy(() -> loader().loadCustomers(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("customers[0]")
                    .hasMessageContaining("hourlyRate must be positive");
        }

        @Test
        void requiresVatIdOrTaxNumberOfTheSeller() throws IOException {
            Path file = write("settings.yaml", SELLER.replace("  vatId: DE123456789\n", "") + "outputDir: out\n");

            assertThatThrownBy(() -> loader().loadSettings(file))
                    .isInstanceOf(ConfigException.class)
                    .hasMessageContaining("seller")
                    .hasMessageContaining("vatId or taxNumber");
        }

        @Test
        void rejectsDuplicateCustomerIds() throws IOException {
            String second = CUSTOMER.substring(CUSTOMER.indexOf("  - id:"));
            Path file = write("customers.yaml", CUSTOMER + second);

            assertThatThrownBy(() -> loader().loadCustomers(file)).hasMessageContaining("duplicate customer id(s): beispiel");
        }

        @Test
        void rejectsAFileWithoutCustomers() throws IOException {
            Path file = write("customers.yaml", "customers: []\n");

            assertThatThrownBy(() -> loader().loadCustomers(file)).hasMessageContaining("no customers defined");
        }

        @Test
        void reportsMissingEmptyAndBrokenFiles() throws IOException {
            Path empty = write("empty.yaml", "");
            Path broken = write("broken.yaml", "customers: [ { id: x\n");

            assertThatThrownBy(() -> loader().loadCustomers(configDir.resolve("missing.yaml")))
                    .isInstanceOf(ConfigException.class).hasMessageContaining("file not found");
            assertThatThrownBy(() -> loader().loadCustomers(empty))
                    .isInstanceOf(ConfigException.class).hasMessageContaining("file is empty");
            assertThatThrownBy(() -> loader().loadCustomers(broken))
                    .isInstanceOf(ConfigException.class).hasMessageContaining("broken.yaml");
        }
    }
}
