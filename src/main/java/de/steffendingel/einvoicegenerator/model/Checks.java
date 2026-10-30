package de.steffendingel.einvoicegenerator.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Argument checks shared by the records in this package. */
final class Checks {

    private Checks() {
    }

    static void requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    /** Returns {@code null} for a missing or blank optional value, otherwise the value itself. */
    static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }

    static void requireNotNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + value);
        }
    }
}
