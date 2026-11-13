package de.steffendingel.einvoicegenerator.einvoice;

import java.io.IOException;

/**
 * Step ⑧: embeds the e-invoice XML into the PDF/A document, which makes it a hybrid e-invoice.
 */
public interface EInvoiceXmlEmbedder {

    /**
     * Embeds the XML into the PDF/A document and writes the PDF/A-3b and e-invoice metadata.
     */
    byte[] embed(byte[] pdf, byte[] xml) throws IOException;
}
