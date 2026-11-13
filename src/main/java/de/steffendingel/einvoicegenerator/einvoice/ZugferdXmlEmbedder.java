package de.steffendingel.einvoicegenerator.einvoice;

import org.mustangproject.ZUGFeRD.PDFAConformanceLevel;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Step ⑧ with Mustang: embeds the XML as {@code factur-x.xml} into the PDF/A-3 document and writes the
 * ZUGFeRD and PDF/A metadata.
 */
public final class ZugferdXmlEmbedder implements EInvoiceXmlEmbedder {

    @Override
    public byte[] embed(byte[] pdf, byte[] xml) throws IOException {
        Objects.requireNonNull(pdf, "pdf must not be null");
        Objects.requireNonNull(xml, "xml must not be null");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
            exporter.setProducer(Zugferd.PRODUCER)
                    .setCreator(Zugferd.PRODUCER)
                    .setProfile(Zugferd.PROFILE)
                    // Mustang would declare PDF/A-3u by default; the documents are created as PDF/A-3b
                    .setConformanceLevel(PDFAConformanceLevel.BASIC)
                    .load(pdf);
            exporter.setXML(xml);
            exporter.export(result);
        } catch (RuntimeException e) {
            throw new IOException("cannot embed the XML into the PDF: " + e.getMessage(), e);
        }
        return result.toByteArray();
    }
}
