package de.steffendingel.einvoicegenerator.model;

import static de.steffendingel.einvoicegenerator.model.Checks.requireText;

import java.util.Arrays;
import java.util.Objects;

/**
 * A timesheet PDF that is appended to the invoice and embedded in the XML.
 *
 * @param fileName file name used for the XML attachment, e.g. "timesheet-2026-10.pdf"
 * @param label    description shown in the PDF ("Anlagen") and in the XML, e.g. "Stundennachweis 10/2026"
 * @param pdf      content of the PDF file
 */
public record Timesheet(String fileName, String label, byte[] pdf) {

    public Timesheet {
        requireText(fileName, "fileName");
        requireText(label, "label");
        Objects.requireNonNull(pdf, "pdf must not be null");
        if (pdf.length == 0) {
            throw new IllegalArgumentException("pdf must not be empty");
        }
        pdf = pdf.clone();
    }

    /** Returns a copy, so the timesheet cannot be changed from outside. */
    @Override
    public byte[] pdf() {
        return pdf.clone();
    }

    // Records compare arrays by reference; compare the content instead.

    @Override
    public boolean equals(Object other) {
        return other instanceof Timesheet that
                && fileName.equals(that.fileName)
                && label.equals(that.label)
                && Arrays.equals(pdf, that.pdf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, label, Arrays.hashCode(pdf));
    }

    @Override
    public String toString() {
        return "Timesheet[fileName=" + fileName + ", label=" + label + ", pdf=" + pdf.length + " bytes]";
    }
}
