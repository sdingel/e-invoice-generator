package de.steffendingel.einvoicegenerator.cli;

import de.steffendingel.einvoicegenerator.InvoiceGenerationException;
import de.steffendingel.einvoicegenerator.InvoiceService;
import de.steffendingel.einvoicegenerator.Step;
import de.steffendingel.einvoicegenerator.format.GermanFormat;
import de.steffendingel.einvoicegenerator.model.BillingPeriod;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.InvoiceRequest;
import de.steffendingel.einvoicegenerator.model.Timesheet;
import de.steffendingel.einvoicegenerator.model.ValidationResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interactive dialog: asks for customer, invoice date, billing period, hours and timesheet, shows a
 * summary and generates the invoice after confirmation. User-facing texts are German (see CLAUDE.md).
 */
public final class InteractiveDialog {

    /** Exit codes of {@link #run()}. */
    public static final int CREATED = 0;
    public static final int FAILED = 1;
    public static final int ABORTED = 2;

    private static final String LINE = "─".repeat(52);
    private static final DateTimeFormatter DATE_INPUT = DateTimeFormatter.ofPattern("d.M.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH_INPUT = DateTimeFormatter.ofPattern("M/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MONTH_OUTPUT = DateTimeFormatter.ofPattern("MM/yyyy");

    private static final Map<Step, String> STEP_TEXTS = Map.ofEntries(
            Map.entry(Step.CALCULATE_INVOICE, "Rechnungsnummer geprüft"),
            Map.entry(Step.FILL_TEMPLATE, "Template gefüllt"),
            Map.entry(Step.RENDER_PDF, "PDF erzeugt"),
            Map.entry(Step.CHECK_TEXT, "Text im PDF geprüft"),
            Map.entry(Step.APPEND_TIMESHEETS, "Nachweis angehängt"),
            Map.entry(Step.CHECK_PDF_A, "PDF/A-3b geprüft"),
            Map.entry(Step.CREATE_XML, "XML erzeugt"),
            Map.entry(Step.EMBED_XML, "XML eingebettet"),
            Map.entry(Step.VALIDATE_E_INVOICE, "E-Rechnung validiert"),
            Map.entry(Step.STORE_INVOICE, "Rechnung gespeichert"),
            Map.entry(Step.INCREMENT_NUMBER, "Rechnungsnummer hochgezählt"));

    private final InvoiceService service;
    private final List<Customer> customers;
    private final Console console;
    private final LocalDate today;
    private final Path workingDirectory;
    private final Path homeDirectory;

    /**
     * @param workingDirectory relative paths of the timesheet refer to this directory
     * @param homeDirectory    "~" in paths refers to this directory
     */
    public InteractiveDialog(InvoiceService service, List<Customer> customers, Console console, LocalDate today,
                             Path workingDirectory, Path homeDirectory) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.customers = List.copyOf(customers);
        this.console = Objects.requireNonNull(console, "console must not be null");
        this.today = Objects.requireNonNull(today, "today must not be null");
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        this.homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory must not be null");
        if (this.customers.isEmpty()) {
            throw new IllegalArgumentException("customers must not be empty");
        }
    }

    /** Runs the dialog; returns {@link #CREATED}, {@link #FAILED} or {@link #ABORTED}. */
    public int run() {
        try {
            return dialog();
        } catch (DialogAbortedException e) {
            console.println();
            console.println("Abgebrochen, es wurde keine Rechnung erzeugt.");
            return ABORTED;
        } catch (IOException e) {
            console.println("✘ Fehler: " + e.getMessage());
            return FAILED;
        }
    }

    private int dialog() throws IOException {
        Customer customer = askCustomer();
        LocalDate invoiceDate = askDate("Rechnungsdatum", today);
        BillingPeriod period = askPeriod(BillingPeriod.previousMonthOf(invoiceDate));

        Optional<String> existing = service.findExisting(customer, period);
        if (existing.isPresent()) {
            console.println("⚠ Für " + customer.shortName() + " gibt es schon eine Rechnung für "
                    + MONTH_OUTPUT.format(period.from()) + ":");
            console.println("  " + existing.get());
            if (!askYesNo("Trotzdem fortfahren?", false)) {
                return abort();
            }
        }

        BigDecimal hours = askHours();
        Timesheet timesheet = askTimesheet(period);
        Invoice invoice = service.prepare(new InvoiceRequest(customer, period, hours, invoiceDate));

        printSummary(invoice, timesheet);
        if (!askYesNo("Rechnung erzeugen?", true)) {
            return abort();
        }
        return generate(invoice, timesheet);
    }

    private Customer askCustomer() {
        console.println("Kunde:");
        for (int i = 0; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            console.println(String.format("  %d) %-12s %s", i + 1, customer.id(), customer.name()));
        }
        while (true) {
            String answer = console.readLine("Auswahl [1]: ").trim();
            if (answer.isEmpty()) {
                return customers.get(0);
            }
            for (int i = 0; i < customers.size(); i++) {
                if (answer.equals(String.valueOf(i + 1)) || answer.equalsIgnoreCase(customers.get(i).id())) {
                    return customers.get(i);
                }
            }
            console.println("  Bitte eine Nummer von 1 bis " + customers.size() + " oder eine Kunden-ID eingeben.");
        }
    }

    private LocalDate askDate(String label, LocalDate defaultValue) {
        while (true) {
            String answer = console.readLine(label + " [" + GermanFormat.date(defaultValue) + "]: ").trim();
            if (answer.isEmpty()) {
                return defaultValue;
            }
            try {
                return LocalDate.parse(answer, DATE_INPUT);
            } catch (DateTimeParseException e) {
                console.println("  Bitte ein Datum wie 09.11.2026 eingeben.");
            }
        }
    }

    private BillingPeriod askPeriod(BillingPeriod defaultValue) {
        String defaultMonth = MONTH_OUTPUT.format(defaultValue.from());
        while (true) {
            String answer = console.readLine("Leistungszeitraum [" + defaultMonth + "]: ").trim();
            if (answer.isEmpty()) {
                return defaultValue;
            }
            try {
                return BillingPeriod.of(YearMonth.parse(answer, MONTH_INPUT));
            } catch (DateTimeParseException e) {
                console.println("  Bitte einen Monat wie 10/2026 eingeben.");
            }
        }
    }

    private BigDecimal askHours() {
        while (true) {
            String answer = console.readLine("Stunden: ").trim().replace(',', '.');
            try {
                BigDecimal hours = new BigDecimal(answer);
                if (hours.signum() > 0) {
                    return hours;
                }
            } catch (NumberFormatException e) {
                // asked again below
            }
            console.println("  Bitte eine positive Zahl eingeben, z. B. 37,5.");
        }
    }

    private Timesheet askTimesheet(BillingPeriod period) throws IOException {
        String label = "Tätigkeitsnachweis " + MONTH_OUTPUT.format(period.from());
        while (true) {
            String answer = console.readPath("Tätigkeitsnachweis (PDF): ").trim();
            Optional<Path> file = resolve(answer);
            if (file.isEmpty()) {
                continue;
            }
            Timesheet timesheet = new Timesheet(file.get().getFileName().toString(), label, Files.readAllBytes(file.get()));
            ValidationResult check = service.checkTimesheet(timesheet);
            if (check.valid()) {
                console.println("✔ Nachweis geeignet");
                return timesheet;
            }
            console.println("✘ Der Nachweis kann nicht verwendet werden: " + check.summary());
            check.problems().forEach(problem -> console.println("  - " + problem));
            console.println("  Bitte eine andere Datei angeben (z. B. als PDF/A exportiert).");
        }
    }

    /** Resolves "~" and relative paths; prints a message and returns empty if there is no such file. */
    private Optional<Path> resolve(String input) {
        if (input.isEmpty()) {
            console.println("  Bitte den Pfad zur PDF-Datei angeben.");
            return Optional.empty();
        }
        String unquoted = input.length() > 1 && input.startsWith("\"") && input.endsWith("\"")
                ? input.substring(1, input.length() - 1) : input;
        Path path;
        try {
            path = unquoted.equals("~") ? homeDirectory
                    : unquoted.startsWith("~/") ? homeDirectory.resolve(unquoted.substring(2))
                    : workingDirectory.resolve(unquoted);
        } catch (InvalidPathException e) {
            console.println("  Ungültiger Pfad: " + input);
            return Optional.empty();
        }
        if (!Files.isRegularFile(path)) {
            console.println("  Datei nicht gefunden: " + path);
            return Optional.empty();
        }
        return Optional.of(path.normalize());
    }

    private boolean askYesNo(String question, boolean defaultValue) {
        while (true) {
            String answer = console.readLine(question + (defaultValue ? " [J/n]: " : " [j/N]: ")).trim().toLowerCase();
            if (answer.isEmpty()) {
                return defaultValue;
            }
            if (answer.equals("j") || answer.equals("ja")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("nein")) {
                return false;
            }
            console.println("  Bitte j oder n eingeben.");
        }
    }

    private void printSummary(Invoice invoice, Timesheet timesheet) {
        console.println(LINE);
        console.println("Rechnung Nr. " + invoice.number() + " an " + invoice.customer().name());
        console.println(row("Rechnungsdatum", GermanFormat.date(invoice.invoiceDate())));
        console.println(row("Leistungszeitraum", GermanFormat.date(invoice.period().from()) + " – "
                + GermanFormat.date(invoice.period().to())));
        console.println(row(GermanFormat.decimal(invoice.hours()) + " h × " + GermanFormat.money(invoice.hourlyRate()),
                amount(invoice.netAmount())));
        console.println(row("zzgl. " + GermanFormat.percent(invoice.vatRate()) + " USt", amount(invoice.vatAmount())));
        console.println(row("Gesamt", amount(invoice.grossAmount())));
        console.println(row("Fällig am", GermanFormat.date(invoice.dueDate())));
        console.println(row("Anlage", timesheet.label() + " (" + timesheet.fileName() + ")"));
        console.println(LINE);
    }

    private static String row(String label, String value) {
        return String.format("%-24s %s", label, value);
    }

    /** "=   676,88 €", right-aligned so that the amounts line up. */
    private static String amount(BigDecimal value) {
        return String.format("= %13s", GermanFormat.money(value));
    }

    private int generate(Invoice invoice, Timesheet timesheet) {
        try {
            String stored = service.generate(invoice, List.of(timesheet),
                    step -> console.println("✔ " + step.number() + " " + STEP_TEXTS.get(step)));
            console.println();
            console.println("Rechnung erzeugt: " + stored);
            return CREATED;
        } catch (InvoiceGenerationException e) {
            console.println("✘ Fehler in Schritt " + e.getMessage());
            e.problems().forEach(problem -> console.println("  - " + problem));
            console.println(e.step() == Step.INCREMENT_NUMBER
                    ? "Die Rechnung wurde gespeichert, aber der Rechnungszähler nicht hochgezählt (siehe oben)."
                    : "Es wurde keine Rechnung gespeichert, die Rechnungsnummer ist weiterhin frei.");
            return FAILED;
        }
    }

    private int abort() {
        console.println("Abgebrochen, es wurde keine Rechnung erzeugt.");
        return ABORTED;
    }
}
