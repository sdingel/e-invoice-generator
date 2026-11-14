package de.steffendingel.einvoicegenerator;

import de.steffendingel.einvoicegenerator.archive.FileInvoiceArchive;
import de.steffendingel.einvoicegenerator.cli.Console;
import de.steffendingel.einvoicegenerator.cli.InteractiveDialog;
import de.steffendingel.einvoicegenerator.cli.JLineConsole;
import de.steffendingel.einvoicegenerator.config.ConfigException;
import de.steffendingel.einvoicegenerator.config.ConfigLoader;
import de.steffendingel.einvoicegenerator.config.Settings;
import de.steffendingel.einvoicegenerator.einvoice.MustangValidator;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlEmbedder;
import de.steffendingel.einvoicegenerator.einvoice.ZugferdXmlGenerator;
import de.steffendingel.einvoicegenerator.html.FreemarkerHtmlRenderer;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.numbering.FileInvoiceNumberSource;
import de.steffendingel.einvoicegenerator.pdf.OpenHtmlToPdfConverter;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxPageAppender;
import de.steffendingel.einvoicegenerator.pdf.PdfBoxTextVerifier;
import de.steffendingel.einvoicegenerator.pdf.VeraPdfValidator;

import com.openhtmltopdf.util.XRLog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point: reads the configuration, creates the implementations of the steps and starts the dialog.
 *
 * <p>Usage: {@code e-invoice-generator [--config <directory>] [--help] [--version]}; the default
 * configuration directory is {@code ~/rechnungen/config}.
 */
public final class App {

    static final String DEFAULT_CONFIG_DIRECTORY = "rechnungen/config";

    private static final String USAGE = """
            Usage: e-invoice-generator [--config <directory>] [--help] [--version]

              --config <directory>  configuration directory with settings.yaml, customers.yaml and
                                    invoice-counter (default: ~/rechnungen/config)
              --help                show this help
              --version             show the version
            """;

    /** What the command line asks for. */
    enum Command { RUN, HELP, VERSION }

    /** Parsed command line. */
    record Options(Command command, Path configDirectory) {}

    private App() {
    }

    public static void main(String[] args) {
        quietLibraryLogging();
        Path home = Path.of(System.getProperty("user.home"));
        int exitCode;
        try (JLineConsole console = new JLineConsole()) {
            exitCode = run(args, console, home, Path.of("").toAbsolutePath(), LocalDate.now(), System.err);
        } catch (IOException e) {
            System.err.println("Cannot open the terminal: " + e.getMessage());
            exitCode = InteractiveDialog.FAILED;
        }
        System.exit(exitCode);
    }

    /**
     * Runs the application; separated from {@link #main} for tests.
     *
     * @return exit code: 0 invoice created, 1 error, 2 aborted by the user
     */
    static int run(String[] args, Console console, Path home, Path workingDirectory, LocalDate today, PrintStream err) {
        Options options;
        try {
            options = parse(args, home);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.print(USAGE);
            return InteractiveDialog.FAILED;
        }
        if (options.command() == Command.HELP) {
            console.println(AppInfo.nameAndVersion() + "\n\n" + USAGE);
            return InteractiveDialog.CREATED;
        }
        if (options.command() == Command.VERSION) {
            console.println(AppInfo.nameAndVersion() + "\n" + AppInfo.URL);
            return InteractiveDialog.CREATED;
        }
        Path configDirectory = options.configDirectory();

        Settings settings;
        List<Customer> customers;
        InvoiceService service;
        try {
            if (!Files.isDirectory(configDirectory)) {
                throw new ConfigException("configuration directory not found: " + configDirectory
                        + ". Examples are in the repository under examples/config/.");
            }
            ConfigLoader loader = new ConfigLoader(home);
            settings = loader.loadSettings(configDirectory.resolve("settings.yaml"));
            customers = loader.loadCustomers(configDirectory.resolve("customers.yaml"));
            service = createService(settings);
        } catch (ConfigException e) {
            err.println("Configuration: " + e.getMessage());
            return InteractiveDialog.FAILED;
        } catch (IOException | IllegalArgumentException e) {
            err.println("Cannot start: " + e.getMessage());
            return InteractiveDialog.FAILED;
        }
        return new InteractiveDialog(service, customers, console, today, workingDirectory, home).run();
    }

    /** Creates the implementations of all steps; fails early, e.g. on a missing font or counter file. */
    static InvoiceService createService(Settings settings) throws IOException {
        FileInvoiceNumberSource numbers = new FileInvoiceNumberSource(settings.counterFile());
        numbers.peekNext(); // missing or invalid counter file: report now, not after all the questions
        Path templateDirectory = settings.templateFile().map(Path::getParent).orElse(null);
        return new InvoiceService(
                numbers,
                new InvoiceCalculator(settings.seller()),
                new FreemarkerHtmlRenderer(settings.template(), settings.logo()),
                new OpenHtmlToPdfConverter(templateDirectory),
                new PdfBoxTextVerifier(),
                new PdfBoxPageAppender(),
                new VeraPdfValidator(),
                new ZugferdXmlGenerator(),
                new ZugferdXmlEmbedder(),
                new MustangValidator(),
                new FileInvoiceArchive(settings.outputDir(), settings.fileNameTemplate()));
    }

    /** Parses the command line; {@code --help} and {@code --version} end the parsing. */
    static Options parse(String[] args, Path home) {
        Path directory = home.resolve(DEFAULT_CONFIG_DIRECTORY);
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    return new Options(Command.HELP, null);
                }
                case "--version" -> {
                    return new Options(Command.VERSION, null);
                }
                case "--config" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--config needs a directory.");
                    }
                    String value = args[++i];
                    // Expanded here because not every shell does it (e.g. cmd.exe); "~\cfg" is the Windows spelling
                    boolean inHome = value.startsWith("~/") || value.startsWith("~\\");
                    directory = inHome ? home.resolve(value.substring(2)) : Path.of(value);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        return new Options(Command.RUN, directory.toAbsolutePath().normalize());
    }

    /**
     * openhtmltopdf and veraPDF log a lot via java.util.logging (Mustang via SLF4J, silenced by slf4j-nop).
     * The tool reports problems itself, so only severe messages are kept.
     */
    static void quietLibraryLogging() {
        XRLog.setLoggingEnabled(false);
        Logger root = Logger.getLogger("");
        root.setLevel(Level.SEVERE);
        for (var handler : root.getHandlers()) {
            handler.setLevel(Level.SEVERE);
        }
    }
}
