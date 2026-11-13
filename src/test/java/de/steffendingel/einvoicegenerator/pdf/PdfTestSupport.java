package de.steffendingel.einvoicegenerator.pdf;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

/** Helpers for PDF tests: PDF/A check with veraPDF, text extraction and example logos. */
final class PdfTestSupport {

    private PdfTestSupport() {
    }

    /** Returns the failed PDF/A rules; empty if the PDF is compliant with the flavour it declares. */
    static List<String> pdfAProblems(byte[] pdf) throws Exception {
        VeraGreenfieldFoundryProvider.initialise();
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf));
             PDFAValidator validator = Foundries.defaultInstance().createValidator(parser.getFlavour(), false)) {
            ValidationResult result = validator.validate(parser);
            if (!"3b".equals(result.getPDFAFlavour().getId())) {
                return List.of("not PDF/A-3b but " + result.getPDFAFlavour().getId());
            }
            return result.getTestAssertions().stream()
                    .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                    .map(a -> a.getRuleId().getClause() + "-" + a.getRuleId().getTestNumber() + ": " + a.getMessage())
                    .distinct()
                    .toList();
        }
    }

    static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    /** A small vector logo as PDF: grey squares and a red square (example, not a real logo). */
    static Path writePdfLogo(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(59, 60));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.65f, 0.65f, 0.65f);
                content.addRect(2, 2, 26, 26);
                content.addRect(30, 2, 26, 26);
                content.fill();
                content.setNonStrokingColor(0.88f, 0.06f, 0.06f);
                content.addRect(30, 31, 26, 26);
                content.fill();
            }
            document.save(file.toFile());
        }
        return file;
    }

    /** A PNG logo with transparency. */
    static Path writePngLogo(Path file) throws IOException {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(166, 166, 166));
        g.fillRect(0, 60, 58, 58);
        g.setColor(new Color(225, 16, 16));
        g.fillRect(62, 0, 58, 58);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
