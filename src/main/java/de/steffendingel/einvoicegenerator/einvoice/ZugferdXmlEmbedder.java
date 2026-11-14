package de.steffendingel.einvoicegenerator.einvoice;

import de.steffendingel.einvoicegenerator.AppInfo;

import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.mustangproject.ZUGFeRD.PDFAConformanceLevel;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Step ⑧ with Mustang: embeds the XML as {@code factur-x.xml} into the PDF/A-3 document and writes the
 * ZUGFeRD and PDF/A metadata. Producer and creator name the tool with its version, the creator tool
 * (XMP) also the project URL.
 */
public final class ZugferdXmlEmbedder implements EInvoiceXmlEmbedder {

    @Override
    public byte[] embed(byte[] pdf, byte[] xml) throws IOException {
        Objects.requireNonNull(pdf, "pdf must not be null");
        Objects.requireNonNull(xml, "xml must not be null");
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (ZUGFeRDExporterFromA3 exporter = new MustangVersionFixingExporter()) {
            exporter.setProducer(AppInfo.nameAndVersion())
                    .setCreator(AppInfo.nameAndVersion())
                    .setCreatorTool(AppInfo.nameAndVersion() + " (" + AppInfo.URL + ")")
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

    /**
     * Mustang appends "(via mustangproject.org &lt;version&gt;)" to the producer in the document information,
     * but in 2.26.0 the version is an unreplaced placeholder "${project.version}". This exporter fills in the
     * Mustang version from our POM; once Mustang writes it itself, there is nothing left to replace.
     */
    private static final class MustangVersionFixingExporter extends ZUGFeRDExporterFromA3 {

        @Override
        protected void writeDocumentInformation() {
            super.writeDocumentInformation();
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setProducer(info.getProducer().replace("${project.version}", AppInfo.MUSTANG_VERSION));
        }
    }
}
