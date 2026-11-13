package de.steffendingel.einvoicegenerator.einvoice;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import org.mustangproject.validator.ZUGFeRDValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Step ⑨ with Mustang's validator: checks PDF/A (veraPDF), the XML schema and the business rules
 * (Schematron) of the hybrid e-invoice, or of the XML alone.
 *
 * <p>Errors and warnings make the result invalid; notices (e.g. rules that only apply to XRechnung)
 * are ignored.
 */
public final class MustangValidator implements EInvoiceValidator {

    @Override
    public ValidationResult validate(byte[] eInvoice) throws IOException {
        Objects.requireNonNull(eInvoice, "eInvoice must not be null");
        // The validator decides by the file name whether it gets a PDF or an XML file
        String fileName = isPdf(eInvoice) ? "invoice.pdf" : "invoice.xml";
        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report;
        try {
            report = validator.validate(eInvoice, fileName);
        } catch (RuntimeException e) {
            throw new IOException("e-invoice validation failed: " + e.getMessage(), e);
        }
        List<String> problems = problems(report);
        if (validator.wasCompletelyValid() && problems.isEmpty()) {
            return ValidationResult.valid("valid e-invoice (ZUGFeRD EN 16931)");
        }
        if (problems.isEmpty()) {
            problems = List.of("the validator reports the e-invoice as invalid without details");
        }
        return ValidationResult.invalid("invalid e-invoice", problems);
    }

    private static boolean isPdf(byte[] data) {
        return data.length >= 4 && new String(data, 0, 4, StandardCharsets.US_ASCII).equals("%PDF");
    }

    /** Collects the texts of all {@code <error>} and {@code <warning>} elements of the report. */
    private static List<String> problems(String report) throws IOException {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(report)));
        } catch (Exception e) {
            throw new IOException("cannot read the validation report: " + e.getMessage(), e);
        }
        List<String> problems = new ArrayList<>();
        for (String type : List.of("error", "warning")) {
            NodeList elements = document.getElementsByTagName(type);
            for (int i = 0; i < elements.getLength(); i++) {
                String text = ((Element) elements.item(i)).getTextContent().replaceAll("\\s+", " ").trim();
                problems.add(type + ": " + (text.isEmpty() ? "(no description)" : text));
            }
        }
        return problems.stream().distinct().toList();
    }
}
