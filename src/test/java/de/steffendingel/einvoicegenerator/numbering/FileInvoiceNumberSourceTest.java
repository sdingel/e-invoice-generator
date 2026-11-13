package de.steffendingel.einvoicegenerator.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileInvoiceNumberSourceTest {

    @TempDir
    Path tempDir;

    private Path counter(String content) throws IOException {
        return Files.writeString(tempDir.resolve("invoice-counter"), content);
    }

    @Test
    void readsTheNextNumber() throws IOException {
        assertThat(new FileInvoiceNumberSource(counter("302\n")).peekNext()).isEqualTo("302");
        assertThat(new FileInvoiceNumberSource(counter("  302  ")).peekNext()).isEqualTo("302");
    }

    @Test
    void peekingDoesNotReserveTheNumber() throws IOException {
        FileInvoiceNumberSource numbers = new FileInvoiceNumberSource(counter("302"));

        numbers.peekNext();

        assertThat(numbers.peekNext()).isEqualTo("302");
    }

    @Test
    void markUsedIncrementsTheCounter() throws IOException {
        Path file = counter("302");
        FileInvoiceNumberSource numbers = new FileInvoiceNumberSource(file);

        numbers.markUsed("302");

        assertThat(numbers.peekNext()).isEqualTo("303");
        assertThat(Files.readString(file).trim()).isEqualTo("303");
    }

    @Test
    void continuesBeyondThreeDigits() throws IOException {
        FileInvoiceNumberSource numbers = new FileInvoiceNumberSource(counter("999"));

        numbers.markUsed("999");

        assertThat(numbers.peekNext()).isEqualTo("1000");
    }

    @Test
    void refusesToMarkAnotherNumber() throws IOException {
        Path file = counter("303");

        assertThatThrownBy(() -> new FileInvoiceNumberSource(file).markUsed("302"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("302 is not the next number 303");
        assertThat(Files.readString(file)).isEqualTo("303");
    }

    @Test
    void explainsWhatToDoIfTheFileIsMissing() {
        assertThatThrownBy(() -> new FileInvoiceNumberSource(tempDir.resolve("invoice-counter")).peekNext())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("invoice counter file not found")
                .hasMessageContaining("Create it with your next invoice number");
    }

    @ParameterizedTest(name = "content \"{0}\"")
    @ValueSource(strings = {"", "abc", "0", "-5", "302 303", "30.2"})
    void rejectsInvalidContent(String content) throws IOException {
        Path file = counter(content);

        assertThatThrownBy(() -> new FileInvoiceNumberSource(file).peekNext())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("must contain only the next invoice number");
    }

    @Test
    void leavesNoTemporaryFiles() throws IOException {
        new FileInvoiceNumberSource(counter("302")).markUsed("302");

        try (Stream<Path> files = Files.list(tempDir)) {
            assertThat(files).extracting(file -> file.getFileName().toString()).containsExactly("invoice-counter");
        }
    }
}
