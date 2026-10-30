package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.optionalText;
import static de.steffendingel.einvoicegenerator.model.Checks.requirePositive;
import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * A customer as configured in {@code customers.yaml}.
 *
 * @param id              short identifier, used e.g. in file names
 * @param vatId           VAT identification number of the customer, optional
 * @param email           invoice e-mail address, optional
 * @param hourlyRate      net price per hour
 * @param paymentTermDays days between invoice date and due date
 * @param itemText        text of the single invoice line
 * @param fields          free placeholders for the template (e.g. contract date), never {@code null}
 */
public record Customer(String id, String name, Address address, String vatId, String email,
                       BigDecimal hourlyRate, int paymentTermDays, String itemText,
                       Map<String, String> fields) {

    public Customer {
        requireText(id, "id");
        requireText(name, "name");
        Objects.requireNonNull(address, "address must not be null");
        vatId = optionalText(vatId);
        email = optionalText(email);
        requirePositive(hourlyRate, "hourlyRate");
        if (paymentTermDays < 0) {
            throw new IllegalArgumentException("paymentTermDays must not be negative: " + paymentTermDays);
        }
        requireText(itemText, "itemText");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
