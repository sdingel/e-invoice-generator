package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.optionalText;
import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

import java.util.Objects;

/**
 * The invoicing party. Contains only data that is also needed in the e-invoice XML; display-only
 * details (phone, website, ...) are part of the template.
 *
 * @param vatId     VAT identification number, optional if a tax number is given
 * @param taxNumber tax number, optional if a VAT identification number is given
 */
public record Seller(String name, Address address, String vatId, String taxNumber,
                     String iban, String bic, String email) {

    public Seller {
        requireText(name, "name");
        Objects.requireNonNull(address, "address must not be null");
        vatId = optionalText(vatId);
        taxNumber = optionalText(taxNumber);
        if (vatId == null && taxNumber == null) {
            throw new IllegalArgumentException("either vatId or taxNumber is required");
        }
        requireText(iban, "iban");
        iban = iban.replace(" ", "");
        bic = optionalText(bic);
        requireText(email, "email");
    }
}
