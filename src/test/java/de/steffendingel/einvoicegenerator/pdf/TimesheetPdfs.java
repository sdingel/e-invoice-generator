package de.steffendingel.einvoicegenerator.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Example timesheets as created by other programs: plain PDFs without PDF/A identification and without
 * output intent (like an Excel export), with variations that are or are not acceptable.
 */
final class TimesheetPdfs {

    enum Font { EMBEDDED, NOT_EMBEDDED }

    enum Color { RGB, CMYK }

    private TimesheetPdfs() {
    }

    /** Plain PDF, A4 portrait, embedded font, RGB: like the Excel export, acceptable after appending. */
    static byte[] plain(int pages) throws IOException {
        return create(pages, PDRectangle.A4, Font.EMBEDDED, Color.RGB, false);
    }

    static byte[] landscape() throws IOException {
        return create(1, new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()),
                Font.EMBEDDED, Color.RGB, false);
    }

    static byte[] withFont(Font font) throws IOException {
        return create(1, PDRectangle.A4, font, Color.RGB, false);
    }

    static byte[] withColor(Color color) throws IOException {
        return create(1, PDRectangle.A4, Font.EMBEDDED, color, false);
    }

    static byte[] passwordProtected() throws IOException {
        return create(1, PDRectangle.A4, Font.EMBEDDED, Color.RGB, true);
    }

    private static byte[] create(int pages, PDRectangle size, Font font, Color color, boolean protect)
            throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont pdfFont = font == Font.EMBEDDED ? carlito(document) : new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(size);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    if (color == Color.CMYK) {
                        content.setNonStrokingColor(0f, 0.9f, 0.9f, 0f);
                    } else {
                        content.setNonStrokingColor(0.1f, 0.1f, 0.1f);
                    }
                    content.beginText();
                    content.setFont(pdfFont, 14);
                    content.newLineAtOffset(72, size.getHeight() - 72);
                    content.showText("Stundennachweis 10/2026 - Seite " + i);
                    content.endText();
                    content.addRect(72, size.getHeight() - 140, 200, 40);
                    content.fill();
                }
            }
            if (protect) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner", "secret", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PDFont carlito(PDDocument document) throws IOException {
        try (InputStream font = TimesheetPdfs.class.getResourceAsStream("/fonts/Carlito-Regular.ttf")) {
            return PDType0Font.load(document, font);
        }
    }
}
