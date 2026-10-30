package de.steffendingel.einvoicegenerator.html;

import de.steffendingel.einvoicegenerator.model.Invoice;

import java.io.IOException;
import java.util.List;

/**
 * Step ②: fills the invoice template with the invoice data.
 */
public interface InvoiceHtmlRenderer {

    /**
     * Returns the invoice as XHTML, ready for conversion to PDF (step ③) and usable as HTML preview.
     *
     * @param attachmentLabels labels of the attachments listed on the invoice, e.g. "Stundennachweis 10/2026"
     * @throws IOException if the template cannot be read or contains an error
     */
    String renderHtml(Invoice invoice, List<String> attachmentLabels) throws IOException;
}
