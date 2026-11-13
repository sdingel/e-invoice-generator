package de.steffendingel.einvoicegenerator.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Step ③ with openhtmltopdf: renders XHTML as PDF/A-3b with the embedded font Carlito and an sRGB
 * color profile.
 *
 * <p>In PDF/A mode, openhtmltopdf silently drops text whose font family is not registered (the page
 * stays empty). Carlito is therefore also registered as "Calibri" (it is metric-compatible) and as the
 * generic "sans-serif"; other families are not available. Step ④ detects missing text.
 *
 * <p>Images: PNG and JPEG, and PDF files as vector graphics (e.g. a logo). SVG is not supported.
 */
public final class OpenHtmlToPdfConverter implements HtmlToPdfConverter {

    static final String DEFAULT_FONT_DIRECTORY = "/fonts/";

    /**
     * Family names under which Carlito is registered. openhtmltopdf maps the generic CSS family
     * "sans-serif" to "SansSerif" internally, so that is the name to register.
     */
    static final List<String> FONT_FAMILIES = List.of("Carlito", "Calibri", "SansSerif");

    private record FontFile(String file, int weight, FontStyle style) {}

    private static final List<FontFile> FONT_FILES = List.of(
            new FontFile("Carlito-Regular.ttf", 400, FontStyle.NORMAL),
            new FontFile("Carlito-Bold.ttf", 700, FontStyle.NORMAL),
            new FontFile("Carlito-Italic.ttf", 400, FontStyle.ITALIC),
            new FontFile("Carlito-BoldItalic.ttf", 700, FontStyle.ITALIC));

    private final Map<FontFile, byte[]> fonts = new HashMap<>();
    private final byte[] colorProfile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();
    private final String baseUri;

    /**
     * @param baseDirectory directory that relative image paths in the XHTML refer to (e.g. the directory
     *                      of an own template), or {@code null}
     * @throws IOException if a font file is missing; checked here so that the problem shows up at
     *                     start-up and not as an empty invoice
     */
    public OpenHtmlToPdfConverter(Path baseDirectory) throws IOException {
        this(DEFAULT_FONT_DIRECTORY, baseDirectory);
    }

    /** With the fonts from another classpath directory (for tests). */
    OpenHtmlToPdfConverter(String fontDirectory, Path baseDirectory) throws IOException {
        for (FontFile font : FONT_FILES) {
            String resource = fontDirectory + font.file();
            try (InputStream in = OpenHtmlToPdfConverter.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IOException("font not found: " + resource);
                }
                fonts.put(font, in.readAllBytes());
            }
        }
        if (baseDirectory != null && !Files.isDirectory(baseDirectory)) {
            throw new IOException("not a directory: " + baseDirectory);
        }
        this.baseUri = baseDirectory == null ? null : baseDirectory.toAbsolutePath().toUri().toString();
    }

    @Override
    public byte[] convert(String xhtml) throws IOException {
        Objects.requireNonNull(xhtml, "xhtml must not be null");
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder()
                .useFastMode()
                .usePdfAConformance(PdfAConformance.PDFA_3_B)
                .useColorProfile(colorProfile)
                .withHtmlContent(xhtml, baseUri)
                .toStream(pdf);
        for (Map.Entry<FontFile, byte[]> font : fonts.entrySet()) {
            byte[] data = font.getValue();
            for (String family : FONT_FAMILIES) {
                builder.useFont(() -> new ByteArrayInputStream(data), family,
                        font.getKey().weight(), font.getKey().style(), true);
            }
        }
        try {
            builder.run();
        } catch (RuntimeException e) {
            // e.g. XHTML that is not well-formed
            throw new IOException("cannot convert XHTML to PDF: " + e.getMessage(), e);
        }
        return pdf.toByteArray();
    }
}
