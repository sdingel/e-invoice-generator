package de.steffendingel.einvoicegenerator.einvoice;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.IOException;

/**
 * Step ⑨: validates the finished e-invoice.
 */
public interface EInvoiceValidator {

    /**
     * @param eInvoice the hybrid e-invoice (PDF with embedded XML) or the XML alone
     * @return valid only if there are neither errors nor warnings
     */
    ValidationResult validate(byte[] eInvoice) throws IOException;
}
