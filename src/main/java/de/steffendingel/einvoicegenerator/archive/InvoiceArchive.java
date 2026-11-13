package de.steffendingel.einvoicegenerator.archive;

import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;

import java.io.IOException;
import java.util.Optional;

/**
 * Step ⑩: stores finished e-invoices.
 */
public interface InvoiceArchive {

    /**
     * Looks for an invoice that was already stored for the same customer and billing period, as a
     * warning about double billing.
     *
     * @return where the existing invoice is stored, or empty
     */
    Optional<String> findExisting(Customer customer, BillingPeriod period) throws IOException;

    /**
     * Stores the e-invoice. Never overwrites an existing file.
     *
     * @return where the invoice was stored
     * @throws IOException if the target already exists or cannot be written
     */
    String store(Invoice invoice, byte[] eInvoice) throws IOException;
}
