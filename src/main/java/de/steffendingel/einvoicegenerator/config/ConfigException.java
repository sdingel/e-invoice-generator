package de.steffendingel.einvoicegenerator.config;

/**
 * A configuration file is missing, unreadable or invalid. The message is meant for the user: it names
 * the file and, if possible, the position of the problem.
 */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
