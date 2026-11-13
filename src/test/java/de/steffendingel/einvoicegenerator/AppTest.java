package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.steffendingel.einvoicegenerator.cli.InteractiveDialog;
import de.steffendingel.einvoicegenerator.cli.ScriptedConsole;
import de.steffendingel.einvoicegenerator.einvoice.MustangValidator;
import de.steffendingel.einvoicegenerator.pdf.OpenHtmlToPdfConverter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The application from the command line to the stored e-invoice, with all real implementations. */
class AppTest {

    private static final Path EXAMPLES = Path.of("examples", "config");

    @TempDir
    Path home;

    @TempDir
    Path workDir;

    private Path configDir;
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    @BeforeEach
    void copyExampleConfiguration() throws IOException {
        configDir = Files.createDirectories(home.resolve("rechnungen/config"));
        Files.copy(EXAMPLES.resolve("settings.yaml"), configDir.resolve("settings.yaml"));
        Files.copy(EXAMPLES.resolve("customers.yaml"), configDir.resolve("customers.yaml"));
        Files.writeString(configDir.resolve("invoice-counter"), "302\n");
    }

    private int run(ScriptedConsole console, String... args) {
        return App.run(args, console, home, workDir, LocalDate.of(2026, 11, 13),
                new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    private String errors() {
        return errors.toString(StandardCharsets.UTF_8);
    }

    @Test
    void createsAValidEInvoiceFromTheExampleConfiguration() throws Exception {
        OpenHtmlToPdfConverter converter = new OpenHtmlToPdfConverter(null);
        Files.write(workDir.resolve("nachweis.pdf"), converter.convert(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nachweis</title></head>"
                        + "<body style=\"font-family: Carlito\"><p>Tätigkeitsnachweis 10/2026</p></body></html>"));
        ScriptedConsole console = new ScriptedConsole("", "02.11.2026", "", "37,5", "nachweis.pdf", "");

        int exitCode = run(console);

        assertThat(exitCode).as(console.transcript() + errors()).isEqualTo(InteractiveDialog.CREATED);
        Path invoice = home.resolve("rechnungen/2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf");
        assertThat(invoice).exists();
        assertThat(new MustangValidator().validate(Files.readAllBytes(invoice)).valid()).isTrue();
        assertThat(Files.readString(configDir.resolve("invoice-counter")).trim()).isEqualTo("303");
        assertThat(console.transcript()).contains("Rechnung erzeugt: " + invoice);
    }

    @Nested
    class Start {

        @Test
        void showsHelp() {
            ScriptedConsole console = new ScriptedConsole();

            assertThat(run(console, "--help")).isEqualTo(InteractiveDialog.CREATED);
            assertThat(console.transcript()).contains("Aufruf: e-invoice-generator [--config <Ordner>]");
        }

        @Test
        void rejectsUnknownOptions() {
            assertThat(run(new ScriptedConsole(), "--konfig", "x")).isEqualTo(InteractiveDialog.FAILED);
            assertThat(errors()).contains("Unbekannte Option: --konfig").contains("Aufruf:");
        }

        @Test
        void usesTheConfigurationDirectoryFromTheCommandLine() throws IOException {
            Path other = Files.createDirectories(workDir.resolve("andere-config"));

            assertThat(run(new ScriptedConsole(), "--config", other.toString())).isEqualTo(InteractiveDialog.FAILED);
            assertThat(errors()).contains(other.resolve("settings.yaml") + ": file not found");
        }

        @Test
        void reportsAMissingConfigurationDirectory() throws IOException {
            Path missing = workDir.resolve("gibt-es-nicht");

            assertThat(run(new ScriptedConsole(), "--config", missing.toString())).isEqualTo(InteractiveDialog.FAILED);
            assertThat(errors()).contains("Konfigurationsordner nicht gefunden: " + missing).contains("examples/config");
        }

        @Test
        void reportsAMissingCounterFileBeforeAskingAnything() throws IOException {
            Files.delete(configDir.resolve("invoice-counter"));
            ScriptedConsole console = new ScriptedConsole();

            assertThat(run(console)).isEqualTo(InteractiveDialog.FAILED);
            assertThat(errors()).contains("invoice counter file not found");
            assertThat(console.transcript()).isEmpty();
        }

        @Test
        void reportsAnInvalidFileNameTemplate() throws IOException {
            Path settings = configDir.resolve("settings.yaml");
            Files.writeString(settings, Files.readString(settings).replace("{number}", "{nummer}"));

            assertThat(run(new ScriptedConsole())).isEqualTo(InteractiveDialog.FAILED);
            assertThat(errors()).contains("unknown placeholder(s) in file name template: {nummer}");
        }
    }

    @Test
    void configDirectoryOption() {
        Path homeDir = Path.of("/home/test");

        assertThat(App.configDirectory(new String[0], homeDir)).isEqualTo(Path.of("/home/test/rechnungen/config"));
        assertThat(App.configDirectory(new String[] {"--config", "~/cfg"}, homeDir)).isEqualTo(Path.of("/home/test/cfg"));
        assertThat(App.configDirectory(new String[] {"-h"}, homeDir)).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> App.configDirectory(new String[] {"--config"}, homeDir));
    }
}
