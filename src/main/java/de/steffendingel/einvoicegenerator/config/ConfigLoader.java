package de.steffendingel.einvoicegenerator.config;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Seller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reads {@code settings.yaml} and {@code customers.yaml}. The YAML is mapped directly onto the records
 * of the model; their constructors validate the values.
 *
 * <p>Unknown keys (typos) and missing numbers are reported as errors. Note: a typo in a required key
 * (e.g. {@code hourlyRat}) is reported as the missing key ({@code hourlyRate must not be null}), because
 * Jackson calls the record constructor before it reports unknown keys. Text values that YAML reads as
 * numbers must be quoted, e.g. {@code zip: "01067"}: unquoted, YAML would drop the leading zero.
 * Paths may start with "~" (home directory) or be relative to the directory of {@code settings.yaml}.
 */
public final class ConfigLoader {

    private final ObjectMapper mapper = createMapper();
    private final Path homeDir;

    /** Resolves "~" to the home directory of the current user. */
    public ConfigLoader() {
        this(Path.of(System.getProperty("user.home")));
    }

    /** Resolves "~" to the given directory (for tests). */
    public ConfigLoader(Path homeDir) {
        this.homeDir = Objects.requireNonNull(homeDir, "homeDir must not be null");
    }

    // Structure of the files; paths are resolved afterwards
    private record SettingsFile(Seller seller, String outputDir, String logo, String template) {}

    private record CustomersFile(List<Customer> customers) {}

    public Settings loadSettings(Path file) throws ConfigException {
        SettingsFile raw = read(file, SettingsFile.class);
        if (raw.seller() == null) {
            throw new ConfigException(file + ": \"seller\" is missing");
        }
        if (raw.outputDir() == null || raw.outputDir().isBlank()) {
            throw new ConfigException(file + ": \"outputDir\" is missing");
        }
        Path baseDir = file.toAbsolutePath().getParent();
        Path outputDir = resolve(raw.outputDir(), baseDir);
        if (Files.isRegularFile(outputDir)) {
            throw new ConfigException(file + ": outputDir is a file, not a directory: " + outputDir);
        }
        return new Settings(raw.seller(), outputDir,
                existingFile(raw.logo(), "logo", baseDir, file),
                existingFile(raw.template(), "template", baseDir, file));
    }

    public List<Customer> loadCustomers(Path file) throws ConfigException {
        CustomersFile raw = read(file, CustomersFile.class);
        List<Customer> customers = raw.customers() == null ? List.of() : List.copyOf(raw.customers());
        if (customers.isEmpty()) {
            throw new ConfigException(file + ": no customers defined");
        }
        Set<String> ids = new HashSet<>();
        Set<String> duplicates = customers.stream()
                .map(Customer::id)
                .filter(id -> !ids.add(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!duplicates.isEmpty()) {
            throw new ConfigException(file + ": duplicate customer id(s): " + String.join(", ", duplicates));
        }
        return customers;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = JsonMapper.builder(new YAMLFactory())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // otherwise a missing number (e.g. paymentTermDays) would silently become 0
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .build();
        // Unquoted numbers for text values (e.g. zip: 01067) would lose leading zeros
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        return mapper;
    }

    private <T> T read(Path file, Class<T> type) throws ConfigException {
        String content;
        try {
            content = Files.readString(file);
        } catch (NoSuchFileException e) {
            throw new ConfigException(file + ": file not found", e);
        } catch (IOException e) {
            throw new ConfigException(file + ": cannot read file: " + e.getMessage(), e);
        }
        if (content.isBlank()) {
            throw new ConfigException(file + ": file is empty");
        }
        try {
            return mapper.readValue(content, type);
        } catch (JsonMappingException e) {
            throw new ConfigException(file + ": " + describe(e), e);
        } catch (JacksonException e) {
            throw new ConfigException(file + ": invalid YAML: " + e.getOriginalMessage(), e);
        }
    }

    /** E.g. "customers[1].hourlyRate: hourlyRate must be positive: 0 (line 14)". */
    private static String describe(JsonMappingException e) {
        String path = e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? "." + ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining())
                .replaceFirst("^\\.", "");
        // Validation errors from the record constructors are the most helpful message
        Throwable cause = e.getCause();
        String message;
        if (cause instanceof IllegalArgumentException || cause instanceof NullPointerException) {
            message = cause.getMessage();
        } else if (e instanceof MismatchedInputException mismatch && mismatch.getTargetType() == String.class
                && e.getOriginalMessage().startsWith("Cannot coerce")) {
            message = "text must be in quotes, e.g. zip: \"01067\" (without quotes, YAML reads a number)";
        } else {
            message = e.getOriginalMessage();
        }
        String line = e.getLocation() != null ? " (line " + e.getLocation().getLineNr() + ")" : "";
        return (path.isEmpty() ? "" : path + ": ") + message + line;
    }

    private Path existingFile(String value, String name, Path baseDir, Path file) throws ConfigException {
        if (value == null || value.isBlank()) {
            return null;
        }
        Path path = resolve(value, baseDir);
        if (!Files.isRegularFile(path)) {
            throw new ConfigException(file + ": " + name + " not found: " + path);
        }
        return path;
    }

    private Path resolve(String value, Path baseDir) {
        String trimmed = value.trim();
        Path path;
        if (trimmed.equals("~")) {
            path = homeDir;
        } else if (trimmed.startsWith("~/")) {
            path = homeDir.resolve(trimmed.substring(2));
        } else {
            path = baseDir.resolve(trimmed);
        }
        return path.toAbsolutePath().normalize();
    }
}
