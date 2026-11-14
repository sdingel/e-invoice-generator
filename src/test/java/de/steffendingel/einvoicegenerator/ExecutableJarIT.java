package de.steffendingel.einvoicegenerator;

import static org.assertj.core.api.Assertions.assertThat;

import de.steffendingel.einvoicegenerator.einvoice.MustangValidator;
import de.steffendingel.einvoicegenerator.pdf.OpenHtmlToPdfConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starts the finished JAR in a separate process, as a user would. Packaging problems (missing service
 * registrations, broken signatures, missing resources) only show up here, not in the unit tests.
 * Runs after the package phase (maven-failsafe-plugin).
 */
class ExecutableJarIT {

    private static final Path TARGET = Path.of("target");
    private static final Path JAR = TARGET.resolve("e-invoice-generator.jar");
    private static final Path SCRIPT = TARGET.resolve("e-invoice-generator");
    private static final Path WINDOWS_SCRIPT = TARGET.resolve("e-invoice-generator.cmd");

    @TempDir
    Path home;

    @TempDir
    Path workDir;

    private record Result(int exitCode, String output) {}

    private Result run(List<String> command, String input) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS"); // would add a line to the output
        builder.environment().put("JAVA_HOME", System.getProperty("java.home")); // start scripts use this Java
        Process process = builder.start();
        process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("process finished").isTrue();
        return new Result(process.exitValue(), output);
    }

    private List<String> java(String... args) {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.home=" + home, "-jar", JAR.toAbsolutePath().toString()));
        command.addAll(List.of(args));
        return command;
    }

    @Test
    void showsHelp() throws Exception {
        Result result = run(java("--help"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Usage: e-invoice-generator [--config <directory>]");
    }

    @Test
    void showsTheVersionFromThePom() throws Exception {
        Result result = run(java("--version"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).startsWith("E-Invoice Generator " + AppInfo.VERSION).doesNotContain("${");
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "bash on Windows (Git Bash, WSL) cannot take Windows paths")
    void startScriptRunsTheJar() throws Exception {
        assertThat(SCRIPT).exists();

        Result result = run(List.of("bash", SCRIPT.toAbsolutePath().toString(), "--help"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Usage: e-invoice-generator");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsStartScriptRunsTheJar() throws Exception {
        Result result = run(List.of("cmd", "/c", WINDOWS_SCRIPT.toAbsolutePath().toString(), "--help"), "");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Usage: e-invoice-generator");
    }

    @Test
    void windowsStartScriptIsPackagedWithWindowsLineEndings() throws IOException {
        String script = Files.readString(WINDOWS_SCRIPT);

        assertThat(script).contains("-jar \"%JAR%\" %*").contains("\r\n").doesNotContain("\r\r\n");
    }

    @Test
    void createsAValidEInvoice() throws Exception {
        Path configDir = Files.createDirectories(home.resolve("rechnungen/config"));
        Files.copy(Path.of("examples/config/settings.yaml"), configDir.resolve("settings.yaml"));
        Files.copy(Path.of("examples/config/customers.yaml"), configDir.resolve("customers.yaml"));
        Files.writeString(configDir.resolve("invoice-counter"), "302\n");
        Files.write(workDir.resolve("nachweis.pdf"), new OpenHtmlToPdfConverter(null).convert(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Nachweis</title></head>"
                        + "<body style=\"font-family: Carlito\"><p>Tätigkeitsnachweis 10/2026</p></body></html>"));

        Result result = run(java(), "\n02.11.2026\n\n37,5\nnachweis.pdf\n\n");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("✔ ⑨ E-invoice validated").contains("Invoice created:");
        Path invoice = home.resolve("rechnungen/2026-11-02 Max Mustermann Beispielkunde Rechnung 302 für 2026-10.pdf");
        assertThat(invoice).exists();
        assertThat(new MustangValidator().validate(Files.readAllBytes(invoice)).valid()).isTrue();
        assertThat(Files.readString(configDir.resolve("invoice-counter")).trim()).isEqualTo("303");
    }
}
