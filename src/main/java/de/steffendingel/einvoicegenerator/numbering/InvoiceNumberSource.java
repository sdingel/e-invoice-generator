package de.steffendingel.einvoicegenerator.numbering;

import java.io.IOException;

/**
 * Invoice numbers: step ① takes the next number, step ⑪ marks it as used.
 */
public interface InvoiceNumberSource {

    /** Next free number, without reserving it. */
    String peekNext() throws IOException;

    /**
     * Marks the number as used, so that {@link #peekNext()} returns the following one.
     *
     * @throws IOException if the number is not the expected next number (e.g. another invoice was
     *                     created in the meantime) or the change cannot be saved
     */
    void markUsed(String number) throws IOException;
}
