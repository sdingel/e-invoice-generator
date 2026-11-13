package de.steffendingel.einvoicegenerator.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Step ⑤ with PDFBox. Pages are imported one by one, so only the page content is copied: the document
 * catalog of the attachments (XMP metadata, output intents, ...) is not taken over.
 */
public final class PdfBoxPageAppender implements PdfPageAppender {

    @Override
    public byte[] append(byte[] basePdf, List<byte[]> attachmentPdfs) throws IOException {
        Objects.requireNonNull(basePdf, "basePdf must not be null");
        Objects.requireNonNull(attachmentPdfs, "attachmentPdfs must not be null");
        if (attachmentPdfs.isEmpty()) {
            return basePdf.clone();
        }
        // Imported pages refer to objects of their source document, so all sources stay open until saved
        List<PDDocument> sources = new ArrayList<>();
        try (PDDocument target = Loader.loadPDF(basePdf)) {
            for (int i = 0; i < attachmentPdfs.size(); i++) {
                PDDocument source = load(attachmentPdfs.get(i), i + 1);
                sources.add(source);
                for (PDPage page : source.getPages()) {
                    target.importPage(page);
                }
            }
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            target.save(result);
            return result.toByteArray();
        } finally {
            for (PDDocument source : sources) {
                source.close();
            }
        }
    }

    private static PDDocument load(byte[] pdf, int number) throws IOException {
        Objects.requireNonNull(pdf, "attachment " + number + " must not be null");
        try {
            return Loader.loadPDF(pdf);
        } catch (InvalidPasswordException e) {
            throw new IOException("attachment " + number + " is password-protected; export it without a password", e);
        } catch (IOException e) {
            throw new IOException("attachment " + number + " is not a readable PDF: " + e.getMessage(), e);
        }
    }
}
