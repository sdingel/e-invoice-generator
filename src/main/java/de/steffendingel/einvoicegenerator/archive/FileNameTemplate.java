package de.steffendingel.einvoicegenerator.archive;

import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File name of an invoice, e.g. {@code "{invoiceDate} {sellerName} {customerShortName} Rechnung {number}
 * für {period}.pdf"} → "2026-11-09 Max Mustermann Beispielkunde Rechnung 304 für 2026-10.pdf".
 *
 * <p>Characters that are not allowed in file names on common systems are replaced by "-".
 */
final class FileNameTemplate {

    static final String DEFAULT = "{invoiceDate} {sellerName} {customerShortName} Rechnung {number} für {period}.pdf";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
    private static final Pattern FORBIDDEN = Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Placeholders and their values; the period is the month in which the billing period starts. */
    private static final Map<String, Function<Invoice, String>> VALUES = Map.of(
            "invoiceDate", invoice -> DATE.format(invoice.invoiceDate()),
            "period", invoice -> MONTH.format(invoice.period().from()),
            "number", Invoice::number,
            "sellerName", invoice -> invoice.seller().name(),
            "customerShortName", invoice -> invoice.customer().shortName(),
            "customerName", invoice -> invoice.customer().name(),
            "customerId", invoice -> invoice.customer().id());

    private final String template;

    /**
     * @param template template or {@code null} for {@link #DEFAULT}; ".pdf" is appended if missing
     * @throws IllegalArgumentException for unknown placeholders or without {@code {number}}
     */
    FileNameTemplate(String template) {
        String value = template == null || template.isBlank() ? DEFAULT : template.trim();
        if (!value.toLowerCase().endsWith(".pdf")) {
            value = value + ".pdf";
        }
        Set<String> unknown = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            if (!VALUES.containsKey(matcher.group(1))) {
                unknown.add("{" + matcher.group(1) + "}");
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown placeholder(s) in file name template: "
                    + String.join(", ", unknown) + " (known: " + String.join(", ", new TreeSet<>(VALUES.keySet()).stream()
                    .map(name -> "{" + name + "}").toList()) + ")");
        }
        if (!value.contains("{number}")) {
            throw new IllegalArgumentException("file name template must contain {number}: " + value);
        }
        this.template = value;
    }

    String fileName(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        return replace(placeholder -> sanitize(VALUES.get(placeholder).apply(invoice)));
    }

    /** Matches file names of any invoice for this customer and period (other date and number). */
    Pattern patternFor(Customer customer, BillingPeriod period) {
        return pattern(Map.of(
                "period", MONTH.format(period.from()),
                "customerShortName", customer.shortName(),
                "customerName", customer.name(),
                "customerId", customer.id()));
    }

    /** Matches file names of any invoice with this number (any date, customer and period). */
    Pattern patternForNumber(String number) {
        return pattern(Map.of("number", number));
    }

    /** Placeholders in {@code fixed} must have that value; all others may have any value. */
    private Pattern pattern(Map<String, String> fixed) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        int last = 0;
        while (matcher.find()) {
            parts.add(Pattern.quote(template.substring(last, matcher.start())));
            String placeholder = matcher.group(1);
            parts.add(fixed.containsKey(placeholder) ? Pattern.quote(sanitize(fixed.get(placeholder))) : ".+?");
            last = matcher.end();
        }
        parts.add(Pattern.quote(template.substring(last)));
        return Pattern.compile(String.join("", parts));
    }

    private String replace(Function<String, String> value) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.apply(matcher.group(1))));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String sanitize(String value) {
        return FORBIDDEN.matcher(value).replaceAll("-").trim();
    }

    @Override
    public String toString() {
        return template;
    }
}
