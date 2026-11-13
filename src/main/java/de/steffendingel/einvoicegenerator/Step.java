package de.steffendingel.einvoicegenerator;

/**
 * The processing steps of the README diagram, in order. Used to report progress and to name the step
 * in which generating an invoice failed.
 */
public enum Step {
    CALCULATE_INVOICE("①"),
    FILL_TEMPLATE("②"),
    RENDER_PDF("③"),
    CHECK_TEXT("④"),
    APPEND_TIMESHEETS("⑤"),
    CHECK_PDF_A("⑥"),
    CREATE_XML("⑦"),
    EMBED_XML("⑧"),
    VALIDATE_E_INVOICE("⑨"),
    STORE_INVOICE("⑩"),
    INCREMENT_NUMBER("⑪");

    private final String number;

    Step(String number) {
        this.number = number;
    }

    /** The circled number of the step in the README diagram, e.g. "⑥". */
    public String number() {
        return number;
    }
}
