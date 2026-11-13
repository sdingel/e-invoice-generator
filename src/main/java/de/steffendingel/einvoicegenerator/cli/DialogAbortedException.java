package de.steffendingel.einvoicegenerator.cli;

/** The user aborted the dialog, e.g. with Ctrl+C. */
public class DialogAbortedException extends RuntimeException {

    public DialogAbortedException() {
        super("aborted by the user");
    }
}
