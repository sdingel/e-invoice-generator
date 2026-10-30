package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

import java.util.List;

/**
 * Result of a check (text read-back, PDF/A, e-invoice validation).
 *
 * @param summary  short description, e.g. "PDF/A-3b compliant"
 * @param problems details of what is wrong; empty if valid
 */
public record ValidationResult(boolean valid, String summary, List<String> problems) {

    public ValidationResult {
        requireText(summary, "summary");
        problems = problems == null ? List.of() : List.copyOf(problems);
        if (valid && !problems.isEmpty()) {
            throw new IllegalArgumentException("a valid result must not have problems");
        }
    }

    public static ValidationResult valid(String summary) {
        return new ValidationResult(true, summary, List.of());
    }

    public static ValidationResult invalid(String summary, List<String> problems) {
        return new ValidationResult(false, summary, problems);
    }
}
