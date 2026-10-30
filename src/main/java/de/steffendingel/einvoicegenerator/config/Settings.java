package de.steffendingel.einvoicegenerator.config;

import de.steffendingel.einvoicegenerator.model.Seller;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Contents of {@code settings.yaml}. Paths are absolute ("~" and relative paths are resolved by
 * {@link ConfigLoader}).
 *
 * @param outputDir directory for the generated invoices
 * @param logo      logo for the template, {@code null} if none is configured
 * @param template  own invoice template, {@code null} to use the built-in one
 */
public record Settings(Seller seller, Path outputDir, Path logo, Path template) {

    public Settings {
        Objects.requireNonNull(seller, "seller must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
    }

    public Optional<Path> logoFile() {
        return Optional.ofNullable(logo);
    }

    public Optional<Path> templateFile() {
        return Optional.ofNullable(template);
    }
}
