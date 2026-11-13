package de.steffendingel.einvoicegenerator.pdf;

import java.io.IOException;
import java.util.List;

/**
 * Step ⑤: appends the pages of the timesheets to the invoice PDF.
 */
public interface PdfPageAppender {

    /**
     * Appends all pages of the attachments, in the given order. Metadata and output intent (color
     * profile) of the base document are kept for the whole document; those of the attachments are
     * dropped. Pages keep their size and orientation.
     *
     * @throws IOException if an attachment cannot be read, e.g. because it is damaged or password-protected
     */
    byte[] append(byte[] basePdf, List<byte[]> attachmentPdfs) throws IOException;
}
