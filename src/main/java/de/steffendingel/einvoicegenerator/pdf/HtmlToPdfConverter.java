package de.steffendingel.einvoicegenerator.pdf;

import java.io.IOException;

/**
 * Step ③: converts the XHTML of the invoice into a PDF/A-3b document.
 */
public interface HtmlToPdfConverter {

    /**
     * Converts XHTML into a PDF/A-3b document with embedded fonts.
     *
     * @throws IOException if the XHTML is not well-formed or the PDF cannot be created
     */
    byte[] convert(String xhtml) throws IOException;
}
