package de.steffendingel.einvoicegenerator.einvoice;

import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.Timesheet;

import java.io.IOException;
import java.util.List;

/**
 * Step ⑦: creates the structured e-invoice XML from the invoice data.
 */
public interface EInvoiceXmlGenerator {

    /**
     * @param timesheets embedded in the XML as additional supporting documents (BG-24)
     * @throws IOException if the XML cannot be created or does not match the calculated amounts
     */
    byte[] generateXml(Invoice invoice, List<Timesheet> timesheets) throws IOException;
}
