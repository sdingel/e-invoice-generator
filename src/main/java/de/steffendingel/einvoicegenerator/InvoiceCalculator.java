package de.steffendingel.einvoicegenerator;

import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.Seller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Step ①: calculates an invoice from the request and the invoice number.
 *
 * <p>There is exactly one line item (hours × hourly rate) and always 19 % VAT (standard rate, category S).
 * Monetary amounts are rounded commercially (half up) to two decimal places.
 */
public final class InvoiceCalculator {

    /** Standard German VAT rate in percent. */
    public static final BigDecimal VAT_RATE = new BigDecimal("19");

    private static final int AMOUNT_SCALE = 2;

    private final Seller seller;

    public InvoiceCalculator(Seller seller) {
        this.seller = Objects.requireNonNull(seller, "seller must not be null");
    }

    public Invoice calculate(InvoiceRequest request, String number) {
        Objects.requireNonNull(request, "request must not be null");
        BigDecimal hourlyRate = request.customer().hourlyRate();

        BigDecimal net = round(request.hours().multiply(hourlyRate));
        BigDecimal vat = round(net.multiply(VAT_RATE).movePointLeft(2));
        BigDecimal gross = net.add(vat);

        return new Invoice(number, request.invoiceDate(),
                request.invoiceDate().plusDays(request.customer().paymentTermDays()),
                request.period(), seller, request.customer(), request.hours(), hourlyRate,
                net, VAT_RATE, vat, gross);
    }

    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
