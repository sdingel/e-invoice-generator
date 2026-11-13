package de.steffendingel.einvoicegenerator.pdf;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Step ④ with PDFBox: extracts the text of the PDF and looks for the expected texts.
 *
 * <p>Whitespace is normalized on both sides (line breaks, non-breaking spaces, multiple spaces), so a
 * text that wraps across lines in the PDF is still found.
 */
public final class PdfBoxTextVerifier implements PdfTextVerifier {

    @Override
    public ValidationResult verify(byte[] pdf, List<String> expectedTexts) throws IOException {
        Objects.requireNonNull(pdf, "pdf must not be null");
        Objects.requireNonNull(expectedTexts, "expectedTexts must not be null");
        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = normalize(new PDFTextStripper().getText(document));
        }
        if (text.isEmpty()) {
            return ValidationResult.invalid("PDF contains no text",
                    List.of("The PDF has no text at all. Does the template use a font that is not available?"));
        }
        List<String> missing = expectedTexts.stream()
                .filter(expected -> !text.contains(normalize(expected)))
                .map(expected -> "missing text: \"" + expected + "\"")
                .toList();
        return missing.isEmpty()
                ? ValidationResult.valid("all " + expectedTexts.size() + " expected texts found")
                : ValidationResult.invalid("expected text missing in PDF", missing);
    }

    private static String normalize(String text) {
        return text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
