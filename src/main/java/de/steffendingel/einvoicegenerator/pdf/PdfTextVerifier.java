package de.steffendingel.einvoicegenerator.pdf;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.IOException;
import java.util.List;

/**
 * Step ④: checks that the rendered PDF really contains the expected texts. Guards against empty pages,
 * e.g. when the template uses a font that is not available.
 */
public interface PdfTextVerifier {

    /**
     * @param expectedTexts texts that must occur, e.g. the invoice number and the total amount
     */
    ValidationResult verify(byte[] pdf, List<String> expectedTexts) throws IOException;
}
