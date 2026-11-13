package de.steffendingel.einvoicegenerator.config;

import de.steffendingel.einvoicegenerator.model.Seller;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Contents of {@code settings.yaml}. Paths are absolute ("~" and relative paths are resolved by
 * {@link ConfigLoader}).
 *
 * @param outputDir        directory for the generated invoices
 * @param logo             logo for the template, {@code null} if none is configured
 * @param template         own invoice template, {@code null} to use the built-in one
 * @param fileNameTemplate template for the file names of invoices, {@code null} for the default
 * @param counterFile      file with the next invoice number ({@code invoice-counter} next to
 *                         {@code settings.yaml})
 */
public record Settings(Seller seller, Path outputDir, Path logo, Path template, String fileNameTemplate,
                       Path counterFile) {

    public Settings {
        Objects.requireNonNull(seller, "seller must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        Objects.requireNonNull(counterFile, "counterFile must not be null");
        fileNameTemplate = fileNameTemplate == null || fileNameTemplate.isBlank() ? null : fileNameTemplate;
    }

    public Optional<Path> logoFile() {
        return Optional.ofNullable(logo);
    }

    public Optional<Path> templateFile() {
        return Optional.ofNullable(template);
    }
}
