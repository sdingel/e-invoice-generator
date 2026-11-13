package de.steffendingel.einvoicegenerator.pdf;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.IOException;

/**
 * Step ⑥: checks that a PDF is PDF/A-3b compliant, e.g. the invoice with the appended timesheet pages.
 */
public interface PdfAValidator {

    /**
     * @return valid if the PDF complies with PDF/A-3b, otherwise the violated rules
     * @throws IOException if the PDF cannot be read at all
     */
    ValidationResult validate(byte[] pdf) throws IOException;
}
