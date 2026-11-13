package de.steffendingel.einvoicegenerator.einvoice;

import de.steffendingel.einvoicegenerator.format.GermanFormat;
import de.steffendingel.einvoicegenerator.model.Address;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.Seller;
import de.steffendingel.einvoicegenerator.model.Timesheet;

import org.mustangproject.BankDetails;
import org.mustangproject.FileAttachment;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Step ⑦ with Mustang: maps the invoice to a Mustang invoice and creates the CII XML (ZUGFeRD,
 * profile EN 16931).
 *
 * <p>Mustang calculates the totals in the XML itself. They are compared with the amounts of our
 * {@link Invoice} (which appear in the PDF), so PDF and XML cannot contradict each other.
 */
public final class ZugferdXmlGenerator implements EInvoiceXmlGenerator {

    /** UN/ECE Recommendation 20 unit code for "hour". */
    private static final String UNIT_HOUR = "HUR";

    @Override
    public byte[] generateXml(Invoice invoice, List<Timesheet> timesheets) throws IOException {
        Objects.requireNonNull(invoice, "invoice must not be null");
        org.mustangproject.Invoice transaction = toMustang(invoice, timesheets == null ? List.of() : timesheets);
        checkTotals(invoice, transaction);

        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Zugferd.PROFILE);
        try {
            provider.generateXML(transaction);
        } catch (RuntimeException e) {
            throw new IOException("cannot create the e-invoice XML: " + e.getMessage(), e);
        }
        return provider.getXML();
    }

    private static org.mustangproject.Invoice toMustang(Invoice invoice, List<Timesheet> timesheets) {
        Product product = new Product(invoice.customer().itemText(), "", UNIT_HOUR, invoice.vatRate());
        org.mustangproject.Invoice transaction = new org.mustangproject.Invoice()
                .setNumber(invoice.number())
                .setIssueDate(Zugferd.date(invoice.invoiceDate()))
                .setDueDate(Zugferd.date(invoice.dueDate()))
                // The billing period is what the PDF shows. The delivery date (BT-72, last day of the
                // period) is not required, but without it Mustang writes an empty delivery element,
                // which the validator reports as a warning (PEPPOL-EN16931-R008).
                .setDetailedDeliveryPeriod(Zugferd.date(invoice.period().from()), Zugferd.date(invoice.period().to()))
                .setDeliveryDate(Zugferd.date(invoice.period().to()))
                .setPaymentTermDescription("Zahlbar bis " + GermanFormat.date(invoice.dueDate()) + " ohne Abzug.")
                .setCurrency("EUR")
                .setSender(seller(invoice.seller()))
                .setRecipient(buyer(invoice.customer()))
                .addItem(new Item(product, invoice.hourlyRate(), invoice.hours()));
        for (Timesheet timesheet : timesheets) {
            transaction.embedFileInXML(new FileAttachment(timesheet.fileName(), "application/pdf", "Data",
                    timesheet.pdf()).setDescription(timesheet.label()));
        }
        return transaction;
    }

    private static TradeParty seller(Seller seller) {
        TradeParty party = party(seller.name(), seller.address()).setEmail(seller.email());
        if (seller.vatId() != null) {
            party.addVATID(seller.vatId());
        }
        if (seller.taxNumber() != null) {
            party.addTaxID(seller.taxNumber());
        }
        party.addBankDetails(seller.bic() == null
                ? new BankDetails(seller.iban())
                : new BankDetails(seller.iban(), seller.bic()));
        return party;
    }

    private static TradeParty buyer(Customer customer) {
        TradeParty party = party(customer.name(), customer.address());
        if (customer.vatId() != null) {
            party.addVATID(customer.vatId());
        }
        if (customer.email() != null) {
            party.setEmail(customer.email());
        }
        return party;
    }

    private static TradeParty party(String name, Address address) {
        return new TradeParty(name, address.street(), address.zip(), address.city(), address.country());
    }

    private static void checkTotals(Invoice invoice, org.mustangproject.Invoice transaction) throws IOException {
        TransactionCalculator calculator = new TransactionCalculator(transaction);
        if (calculator.getTaxBasis().compareTo(invoice.netAmount()) != 0
                || calculator.getGrandTotal().compareTo(invoice.grossAmount()) != 0) {
            throw new IOException("amounts in the XML would differ from the invoice: net "
                    + calculator.getTaxBasis() + " vs. " + invoice.netAmount() + ", gross "
                    + calculator.getGrandTotal() + " vs. " + invoice.grossAmount());
        }
    }
}
