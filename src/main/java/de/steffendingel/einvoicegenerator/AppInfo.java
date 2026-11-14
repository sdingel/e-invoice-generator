package de.steffendingel.einvoicegenerator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * Name, version and project URL of the tool and the Mustang version, taken from {@code pom.xml} (filtered resource
 * {@code app.properties}), so that the version is defined in one place only.
 */
public final class AppInfo {

    /** For example "E-Invoice Generator". */
    public static final String NAME;

    /** For example "0.1.0". */
    public static final String VERSION;

    /** Project page, for example "https://github.com/sdingel/e-invoice-generator". */
    public static final String URL;

    /** Version of the Mustang library, for example "2.26.0"; written by Mustang into the PDF producer. */
    public static final String MUSTANG_VERSION;

    static {
        Properties properties = new Properties();
        try (InputStream in = AppInfo.class.getResourceAsStream("app.properties")) {
            if (in == null) {
                throw new IllegalStateException("app.properties is missing from the class path");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        NAME = properties.getProperty("name");
        VERSION = properties.getProperty("version");
        URL = properties.getProperty("url");
        MUSTANG_VERSION = properties.getProperty("mustangVersion");
    }

    private AppInfo() {
    }

    /** For example "E-Invoice Generator 0.1.0". */
    public static String nameAndVersion() {
        return NAME + " " + VERSION;
    }
}
