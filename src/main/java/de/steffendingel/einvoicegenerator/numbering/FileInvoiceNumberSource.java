package de.steffendingel.einvoicegenerator.numbering;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Invoice numbers from a text file that contains only the next number, e.g. {@code 302}. Numbers are
 * purely numeric and continue across years.
 *
 * <p>The file must exist: starting at 1 by accident would silently create duplicate numbers. It is
 * written to a temporary file first and then renamed, so it is never left half-written.
 */
public final class FileInvoiceNumberSource implements InvoiceNumberSource {

    private final Path file;

    public FileInvoiceNumberSource(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null").toAbsolutePath();
    }

    @Override
    public String peekNext() throws IOException {
        return String.valueOf(read());
    }

    @Override
    public void markUsed(String number) throws IOException {
        Objects.requireNonNull(number, "number must not be null");
        long next = read();
        if (!String.valueOf(next).equals(number.trim())) {
            throw new IOException("invoice number " + number + " is not the next number " + next + " in " + file
                    + " (was another invoice created in the meantime?)");
        }
        write(next + 1);
    }

    private long read() throws IOException {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (NoSuchFileException e) {
            throw new IOException("invoice counter file not found: " + file
                    + ". Create it with your next invoice number as its only content, e.g. 302", e);
        }
        try {
            long value = Long.parseLong(content);
            if (value < 1) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IOException("invoice counter file " + file
                    + " must contain only the next invoice number (a positive whole number), but contains: \""
                    + content + "\"", e);
        }
    }

    private void write(long value) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), ".invoice-counter-", ".tmp");
        try {
            Files.writeString(temporary, value + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
