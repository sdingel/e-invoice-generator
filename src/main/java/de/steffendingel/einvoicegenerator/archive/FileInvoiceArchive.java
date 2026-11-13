package de.steffendingel.einvoicegenerator.archive;

import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Step ⑩ in a directory: one file per invoice, no subdirectories. The file name comes from a
 * {@link FileNameTemplate}.
 *
 * <p>Files are written to a temporary file first and then renamed, so a crash never leaves a half-written
 * invoice; an existing file is never overwritten.
 */
public final class FileInvoiceArchive implements InvoiceArchive {

    private final Path directory;
    private final FileNameTemplate fileNameTemplate;

    /**
     * @param fileNameTemplate template for the file names, or {@code null} for the default
     * @throws IllegalArgumentException if the template is invalid
     */
    public FileInvoiceArchive(Path directory, String fileNameTemplate) {
        this.directory = Objects.requireNonNull(directory, "directory must not be null").toAbsolutePath();
        this.fileNameTemplate = new FileNameTemplate(fileNameTemplate);
    }

    private Path resolve(String fileName) throws IOException {
        try {
            return directory.resolve(fileName);
        } catch (InvalidPathException e) {
            // Java encodes file names with the system locale; without UTF-8 e.g. "ü" cannot be used
            throw new IOException("the file name \"" + fileName + "\" cannot be used on this system. Use a UTF-8"
                    + " locale (e.g. LANG=C.UTF-8) or a file name template without such characters", e);
        }
    }

    @Override
    public Optional<String> findExisting(Customer customer, BillingPeriod period) throws IOException {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(period, "period must not be null");
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        Pattern pattern = fileNameTemplate.patternFor(customer, period);
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> pattern.matcher(file.getFileName().toString()).matches())
                    .map(Path::toString)
                    .sorted()
                    .findFirst();
        }
    }

    @Override
    public String store(Invoice invoice, byte[] eInvoice) throws IOException {
        Objects.requireNonNull(invoice, "invoice must not be null");
        Objects.requireNonNull(eInvoice, "eInvoice must not be null");
        Files.createDirectories(directory);
        Path target = resolve(fileNameTemplate.fileName(invoice));
        if (Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString(), null, "invoice file already exists, not overwritten");
        }
        Path temporary = Files.createTempFile(directory, ".invoice-", ".tmp");
        try {
            Files.write(temporary, eInvoice);
            // Without options, move refuses to replace an existing file (ATOMIC_MOVE would silently replace
            // it on Linux); within the same directory it is a rename, so the file appears complete at once
            Files.move(temporary, target);
            if (Files.size(target) != eInvoice.length) {
                throw new IOException("invoice file was not written completely: " + target);
            }
            return target.toString();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
