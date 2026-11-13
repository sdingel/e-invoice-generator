package de.steffendingel.einvoicegenerator;

import java.util.List;
import java.util.Objects;

/**
 * Generating an invoice failed in a step: a check found problems or a step could not be carried out.
 */
public class InvoiceGenerationException extends Exception {

    private final Step step;
    private final List<String> problems;

    public InvoiceGenerationException(Step step, String message, List<String> problems, Throwable cause) {
        super(step.number() + " " + message, cause);
        this.step = Objects.requireNonNull(step, "step must not be null");
        this.problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public InvoiceGenerationException(Step step, String message, Throwable cause) {
        this(step, message, List.of(), cause);
    }

    /** The step that failed. */
    public Step step() {
        return step;
    }

    /** Details, e.g. the violated PDF/A rules; may be empty. */
    public List<String> problems() {
        return problems;
    }
}
