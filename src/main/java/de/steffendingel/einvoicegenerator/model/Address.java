package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

/**
 * Postal address.
 *
 * @param street  street and house number
 * @param zip     postal code, kept as text because of leading zeros (e.g. "01067")
 * @param city    city
 * @param country ISO 3166-1 alpha-2 country code, e.g. "DE"
 */
public record Address(String street, String zip, String city, String country) {

    public Address {
        requireText(street, "street");
        requireText(zip, "zip");
        requireText(city, "city");
        requireText(country, "country");
        if (!country.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("country must be an ISO 3166-1 alpha-2 code like \"DE\": " + country);
        }
    }
}
