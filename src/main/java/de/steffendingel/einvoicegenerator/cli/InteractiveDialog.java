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
 * summary and generates the invoice after confirmation. The texts are English; the invoice itself is
 * German (formats, timesheet label).
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
            Map.entry(Step.CALCULATE_INVOICE, "Invoice number checked"),
            Map.entry(Step.FILL_TEMPLATE, "Template filled"),
            Map.entry(Step.RENDER_PDF, "PDF created"),
            Map.entry(Step.CHECK_TEXT, "Text in the PDF checked"),
            Map.entry(Step.APPEND_TIMESHEETS, "Timesheet appended"),
            Map.entry(Step.CHECK_PDF_A, "PDF/A-3b checked"),
            Map.entry(Step.CREATE_XML, "XML created"),
            Map.entry(Step.EMBED_XML, "XML embedded"),
            Map.entry(Step.VALIDATE_E_INVOICE, "E-invoice validated"),
            Map.entry(Step.STORE_INVOICE, "Invoice stored"),
            Map.entry(Step.INCREMENT_NUMBER, "Invoice number incremented"));

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
            console.println("Aborted, no invoice was created.");
            return ABORTED;
        } catch (IOException e) {
            console.println("✘ Error: " + e.getMessage());
            return FAILED;
        }
    }

    private int dialog() throws IOException {
        Customer customer = askCustomer();
        LocalDate invoiceDate = askDate("Invoice date", today);
        BillingPeriod period = askPeriod(BillingPeriod.previousMonthOf(invoiceDate));

        Optional<String> existing = service.findExisting(customer, period);
        if (existing.isPresent()) {
            console.println("⚠ There is already an invoice for " + customer.shortName() + " for "
                    + MONTH_OUTPUT.format(period.from()) + ":");
            console.println("  " + existing.get());
            if (!askYesNo("Continue anyway?", false)) {
                return abort();
            }
        }

        BigDecimal hours = askHours();
        Timesheet timesheet = askTimesheet(period);
        Invoice invoice = service.prepare(new InvoiceRequest(customer, period, hours, invoiceDate));

        printSummary(invoice, timesheet);
        if (!askYesNo("Create the invoice?", true)) {
            return abort();
        }
        return generate(invoice, timesheet);
    }

    private Customer askCustomer() {
        console.println("Customer:");
        for (int i = 0; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            console.println(String.format("  %d) %-12s %s", i + 1, customer.id(), customer.name()));
        }
        while (true) {
            String answer = console.readLine("Choice [1]: ").trim();
            if (answer.isEmpty()) {
                return customers.get(0);
            }
            for (int i = 0; i < customers.size(); i++) {
                if (answer.equals(String.valueOf(i + 1)) || answer.equalsIgnoreCase(customers.get(i).id())) {
                    return customers.get(i);
                }
            }
            console.println("  Please enter a number from 1 to " + customers.size() + " or a customer ID.");
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
                console.println("  Please enter a date like 09.11.2026 (day.month.year).");
            }
        }
    }

    private BillingPeriod askPeriod(BillingPeriod defaultValue) {
        String defaultMonth = MONTH_OUTPUT.format(defaultValue.from());
        while (true) {
            String answer = console.readLine("Billing period [" + defaultMonth + "]: ").trim();
            if (answer.isEmpty()) {
                return defaultValue;
            }
            try {
                return BillingPeriod.of(YearMonth.parse(answer, MONTH_INPUT));
            } catch (DateTimeParseException e) {
                console.println("  Please enter a month like 10/2026.");
            }
        }
    }

    private BigDecimal askHours() {
        while (true) {
            String answer = console.readLine("Hours: ").trim().replace(',', '.');
            try {
                BigDecimal hours = new BigDecimal(answer);
                if (hours.signum() > 0) {
                    return hours;
                }
            } catch (NumberFormatException e) {
                // asked again below
            }
            console.println("  Please enter a positive number, e.g. 37.5.");
        }
    }

    private Timesheet askTimesheet(BillingPeriod period) throws IOException {
        String label = "Tätigkeitsnachweis " + MONTH_OUTPUT.format(period.from());
        while (true) {
            String answer = console.readPath("Timesheet (PDF): ").trim();
            Optional<Path> file = resolve(answer);
            if (file.isEmpty()) {
                continue;
            }
            Timesheet timesheet = new Timesheet(file.get().getFileName().toString(), label, Files.readAllBytes(file.get()));
            ValidationResult check = service.checkTimesheet(timesheet);
            if (check.valid()) {
                console.println("✔ Timesheet is suitable");
                return timesheet;
            }
            console.println("✘ The timesheet cannot be used: " + check.summary());
            check.problems().forEach(problem -> console.println("  - " + problem));
            console.println("  Please choose another file (e.g. exported as PDF/A).");
        }
    }

    /** Resolves "~" and relative paths; prints a message and returns empty if there is no such file. */
    private Optional<Path> resolve(String input) {
        if (input.isEmpty()) {
            console.println("  Please enter the path to the PDF file.");
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
            console.println("  Invalid path: " + input);
            return Optional.empty();
        }
        if (!Files.isRegularFile(path)) {
            console.println("  File not found: " + path);
            return Optional.empty();
        }
        return Optional.of(path.normalize());
    }

    private boolean askYesNo(String question, boolean defaultValue) {
        while (true) {
            String answer = console.readLine(question + (defaultValue ? " [Y/n]: " : " [y/N]: ")).trim().toLowerCase();
            if (answer.isEmpty()) {
                return defaultValue;
            }
            if (answer.equals("y") || answer.equals("yes")) {
                return true;
            }
            if (answer.equals("n") || answer.equals("no")) {
                return false;
            }
            console.println("  Please enter y or n.");
        }
    }

    private void printSummary(Invoice invoice, Timesheet timesheet) {
        console.println(LINE);
        console.println("Invoice no. " + invoice.number() + " to " + invoice.customer().name());
        console.println(row("Invoice date", GermanFormat.date(invoice.invoiceDate())));
        console.println(row("Billing period", GermanFormat.date(invoice.period().from()) + " – "
                + GermanFormat.date(invoice.period().to())));
        console.println(row(GermanFormat.decimal(invoice.hours()) + " h × " + GermanFormat.money(invoice.hourlyRate()),
                amount(invoice.netAmount())));
        console.println(row("plus " + GermanFormat.percent(invoice.vatRate()) + " VAT", amount(invoice.vatAmount())));
        console.println(row("Total", amount(invoice.grossAmount())));
        console.println(row("Due date", GermanFormat.date(invoice.dueDate())));
        console.println(row("Attachment", timesheet.label() + " (" + timesheet.fileName() + ")"));
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
            console.println("Invoice created: " + stored);
            return CREATED;
        } catch (InvoiceGenerationException e) {
            console.println("✘ Error in step " + e.getMessage());
            e.problems().forEach(problem -> console.println("  - " + problem));
            console.println(e.step() == Step.INCREMENT_NUMBER
                    ? "The invoice was stored, but the invoice counter was not incremented (see above)."
                    : "No invoice was stored; the invoice number is still free.");
            return FAILED;
        }
    }

    private int abort() {
        console.println("Aborted, no invoice was created.");
        return ABORTED;
    }
}
