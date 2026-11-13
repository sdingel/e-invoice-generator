package de.steffendingel.einvoicegenerator.cli;

/**
 * Input and output of the dialog. Separated from the terminal so that the dialog can be tested with
 * prepared answers.
 */
public interface Console {

    /**
     * Reads a line.
     *
     * @throws DialogAbortedException if the user aborts (Ctrl+C, Ctrl+D)
     */
    String readLine(String prompt);

    /** Reads a file path, with file name completion where the console supports it. */
    String readPath(String prompt);

    void println(String line);

    default void println() {
        println("");
    }
}
