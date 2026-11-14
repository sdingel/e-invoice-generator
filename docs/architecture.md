# Architecture

How the E-Invoice Generator is built and why. For users, see the [README](../README.md); planning
happens in [GitHub issues](https://github.com/sdingel/e-invoice-generator/issues).

## Processing steps

Each step of the diagram in the README has a number and exactly one interface with a defined input
and output, so the steps can be implemented, tested and replaced one by one.

| No. | Step | Interface (implementation) | Input | Output |
|---|---|---|---|---|
| ① | Calculate invoice | – (class `InvoiceCalculator`) | `InvoiceRequest`, invoice number | `Invoice` |
| ② | Fill template | `InvoiceHtmlRenderer` (FreeMarker) | `Invoice`, attachment labels | XHTML |
| ③ | Render to PDF/A | `HtmlToPdfConverter` (openhtmltopdf) | XHTML | invoice PDF (PDF/A-3b) |
| ④ | Read text back | `PdfTextVerifier` (PDFBox) | PDF, expected texts | `ValidationResult` |
| ⑤ | Append timesheet pages | `PdfPageAppender` (PDFBox) | invoice PDF, timesheet PDFs | merged PDF |
| ⑥ | Check PDF/A | `PdfAValidator` (veraPDF) | PDF | `ValidationResult` |
| ⑦ | Create XML | `EInvoiceXmlGenerator` (Mustang) | `Invoice`, timesheets | CII XML (EN 16931, timesheets as BG-24) |
| ⑧ | Embed XML | `EInvoiceXmlEmbedder` (Mustang) | merged PDF, XML | hybrid e-invoice (PDF/A-3b) |
| ⑨ | Validate e-invoice | `EInvoiceValidator` (Mustang validator) | e-invoice | `ValidationResult` |
| ⑩ | Store invoice | `InvoiceArchive` (file system) | `Invoice`, e-invoice | stored file |
| ⑪ | Increment invoice number | `InvoiceNumberSource` (counter file) | used number | – |

`InvoiceService` runs the steps and knows only the interfaces:

- `checkTimesheet`: runs ⑤ and ⑥ on trial right after the timesheet is entered (the timesheet is
  appended to an empty PDF/A-3b page), so problems show up before the invoice is created. No own
  interface is needed for this.
- `findExisting`: warning about double billing (same customer and month).
- `prepare`: takes the next number (`peekNext`) and runs ①; the dialog shows the result as summary.
- `generate`: after confirmation ② → ⑪. Every check aborts. The service reports each finished step
  (`Step`) to a `ProgressListener`; failures are `InvoiceGenerationException`s with step and problems.

Rules for the order and for failures:

- The number is used up only in ⑪, so an abort loses no number. `generate` first checks that the
  number is still the next one.
- Nothing is written before ⑩, after all checks; a failure leaves no file behind.
- ⑤ must run before ⑧: appending pages after embedding the XML breaks the XMP metadata and the
  PDF/A conformance.
- If ⑪ fails after ⑩, the message names the stored file and asks to set the counter by hand; the
  duplicate number check in ⑩ catches a forgotten counter later.

**⑦ and ⑧ are separate** (unlike the usual single Mustang call): Mustang creates the XML on its own
(`ZUGFeRD2PullProvider`) and embeds a finished XML (`setXML`); the embedded XML is byte-identical. So ⑦
can be tested without a PDF (e.g. the dates in the XML), and a pure XML format would only need ⑦. The
ZUGFeRD profile (EN 16931) is one shared constant for ⑦ and ⑧.

## Packages

```
de.steffendingel.einvoicegenerator
├── App, AppInfo                # entry point, wiring; name and version from pom.xml
├── InvoiceService, Step, ...   # process ①–⑪, knows only the interfaces
├── InvoiceCalculator           # ① pure calculation
├── model/                      # records without logic: Address, Seller, Customer, BillingPeriod,
│                               # InvoiceRequest, Invoice, Timesheet, ValidationResult
├── config/                     # Settings, ConfigLoader (YAML)
├── format/                     # GermanFormat (amounts and dates on the invoice)
├── html/                       # ②
├── pdf/                        # ③–⑥
├── einvoice/                   # ⑦–⑨ (format-neutral interfaces, ZUGFeRD implementations)
├── archive/                    # ⑩
├── numbering/                  # ① number, ⑪
└── cli/                        # interactive dialog (JLine)
```

- **Every interface lives next to its implementation**, so everything about one topic (e.g. PDF) is in
  one package.
- `Seller` and `Customer` are domain terms and live in `model`, although they are read from the
  configuration; `config` holds only pure configuration.
- Dependencies:
  - `model` depends on no other package; `format` only on `model`.
  - `config`, `html`, `pdf`, `einvoice`, `archive` and `numbering` depend only on `model` (and `html`
    on `format`), not on each other.
  - `InvoiceService` and `InvoiceCalculator` know `model`, `format` and the interfaces of the step
    packages, not their implementations.
  - `cli` and `App` know everything and connect it.
- `App` and `InvoiceService` are separate so that the process can be tested with stand-ins (e.g. an
  archive in memory); `InvoiceCalculator` is separate so that the calculation (rounding, due date) can be
  tested without any other dependency.

Further design decisions:

- **`byte[]` instead of files:** invoices and timesheets are small (a few 100 KB), so the steps are easy
  to chain and test in memory. Only `InvoiceArchive`, `InvoiceNumberSource` and `ConfigLoader` work with
  files.
- **Invoice number as `String`**, because it goes into the XML and the file name like that; the counter
  itself is numeric.
- **Errors:** technical errors are `IOException`s, check results are `ValidationResult`s.
- **Configuration:** Jackson reads the YAML files directly into the records (no annotations).
  `FAIL_ON_UNKNOWN_PROPERTIES` reports typos with their path; numbers where text is expected (e.g. a
  postcode with a leading zero written without quotes) are rejected.

## Libraries and their pitfalls

| Area | Library |
|---|---|
| ZUGFeRD XML, embedding, validation | [Mustang](https://github.com/ZUGFeRD/mustangproject) (`library`, `validator`) |
| Template | [FreeMarker](https://freemarker.apache.org/) |
| XHTML → PDF/A | [openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf) (fork `io.github.openhtmltopdf`) |
| PDF manipulation, text check | [PDFBox](https://pdfbox.apache.org/) |
| PDF/A check | [veraPDF](https://verapdf.org/) |
| Configuration | Jackson with `jackson-dataformat-yaml` |
| Console | [JLine](https://jline.org/) |

### PDFBox version

Mustang and openhtmltopdf both use PDFBox 3. The old `com.openhtmltopdf` (last release 2021) still uses
PDFBox 2, hence the fork. PDFBox is a direct dependency (the code uses its API) and so sets the version
for all. The `maven-enforcer-plugin` checks on every build:

- `requireMavenVersion` and `requireJavaVersion`
- `requireUpperBoundDeps`: no library in an older version than required anywhere. It found
  `commons-logging` 1.3.0 (Mustang → FOP) instead of 1.4.0 (PDFBox); that is the only entry in
  `dependencyManagement`.
- `dependencyConvergence`, limited to `org.apache.pdfbox`: for all dependencies it fails on Mustang's own
  tree (slf4j, ph-commons, Saxon and others in slightly different versions).

### FreeMarker

- `Configuration.VERSION_2_3_35`: record components can be read as `${customer.name}` only from
  `incompatible_improvements` 2.3.33.
- Templates end in `.ftlh`, and standard file extensions are recognized, so FreeMarker escapes `&`, `<`
  and `>`. Without that, the XHTML would be invalid and openhtmltopdf would fail.
- Amounts and dates are formatted in Java (`GermanFormat`, available in the template as `format`).

### openhtmltopdf

- **Missing fonts give empty pages:** in PDF/A mode, text in a font family that is not registered is
  left out silently (also for `sans-serif` or without any `font-family`), and veraPDF still reports the
  page as compliant. Therefore:
  - Carlito is registered as "Carlito", "Calibri" and "SansSerif" (openhtmltopdf's internal name for
    `sans-serif`); there are no other fonts.
  - Missing font files are an error when the converter is created.
  - Step ④ reads the text back and requires invoice number and total amount.
- Without PDF/A mode, openhtmltopdf would fall back to a non-embedded Times-Roman; it never uses system
  fonts.
- The sRGB color profile comes from the JDK (`ICC_Profile.getInstance(ColorSpace.CS_sRGB)`).
- Content outside the page box is cut off, so the left and right page margins are padding of `body`.
- The logo is PDF (stays vector, no extra library), PNG or JPEG. SVG would need Batik and is not
  supported.

### Timesheets (PDFBox, veraPDF)

- A timesheet does not have to be PDF/A itself: an Excel export without PDF/A identification and
  without color profile, appended to the invoice, gives valid PDF/A-3b, because the output intent and the
  identification of the invoice apply to the whole document.
- Pages are imported one by one (size and orientation are kept); metadata and color profile of the
  invoice apply to the whole document, those of the timesheet are dropped.
- Typical problems found by ⑥: fonts not embedded, CMYK colors (only one output intent, sRGB, is
  allowed), encryption. Password-protected or damaged files give a clear error.
- veraPDF always checks against PDF/A-3b, not against the level a file declares.
- The timesheet is both visible pages and BG-24 in the XML ("additional supporting documents", type code
  916), so it also reaches recipients that only process the XML. It is in the file twice (e.g. 26 KB
  timesheet → 41 KB XML), which was accepted (see issue #31 for making it configurable).

### Mustang

- **Conformance level:** Mustang declares PDF/A-3**u** by default. That failed in veraPDF (rule
  6.2.11.7.2, missing Unicode mapping of single characters), although Mustang's own validator reported
  "valid". The embedder sets `PDFAConformanceLevel.BASIC` → 3b.
- **Dates:** Mustang takes `java.util.Date` and formats it with the time zone that was the default when
  its formatter was created, once per thread. Midnight in another zone shifts every date in the XML by
  a day without any validator noticing. Dates are therefore passed as noon in the default zone
  (`Zugferd.date`); a test checks the dates in the XML with several time zones.
- **Delivery date (BT-72)** is set to the last day of the billing period: it is optional, but without it
  Mustang writes an empty element, which gives warning PEPPOL-EN16931-R008.
- **No buyer or order reference** (BT-10, BT-13) until a customer asks for it.
- **Totals:** Mustang calculates the totals itself; ⑦ compares them with the `Invoice`, so PDF and XML
  cannot disagree.
- **Validation:** warnings count as errors; notices (e.g. XRechnung rules such as buyer reference or
  electronic address) are ignored, they do not apply to ZUGFeRD EN 16931.
- **Metadata:** producer and creator name the tool and version, the creator and the XMP creator tool also
  the project URL. Mustang 2.26.0 appends "(via mustangproject.org ${project.version})" to the producer
  in the document information with an unreplaced placeholder; the embedder fills in the Mustang version
  from `pom.xml`. That producer differs from the XMP producer, which PDF/A-3 allows.

## Storage and numbering

- The counter file holds the **next** number. It is increased only after the invoice was stored, and
  written via a temporary file. A missing file is an error (no silent start at 1); it is checked at
  start-up, not after all questions.
- Stored invoices are never overwritten: written to a temporary file, then moved without replacing
  (`ATOMIC_MOVE` would replace on Linux).
- File names come from a template. The archive finds earlier invoices by matching file names against
  the template: same customer and month → warning; same number → error (e.g. after resetting the
  counter). After a change of the template, older files are no longer recognized.
- Non-ASCII file names need a UTF-8 locale; without it there is a clear error (tests set
  `LC_ALL=C.UTF-8`).

## Build and packaging

- Java 17 is the minimum version (compiler `release` 17, enforcer, CI). JLine is used in its `jdk11`
  variant, without the FFM terminal classes for Java 22, so every class in the JAR runs on Java 17.
- Name, version and project URL are defined only in `pom.xml`. Maven filters them into
  `app.properties` (own directory `src/main/resources-filtered/`, because filtering would damage the
  fonts and the `${…}` expressions of the templates); `AppInfo` reads them. The manifest is not used,
  because it exists only in the finished JAR, not in tests or `exec:java`.
- The JAR contains all libraries (`maven-shade-plugin`): signature files of the libraries removed,
  `META-INF/services` merged (FOP, ph-commons and Jackson ship files with the same name; without merging
  they would be lost silently), `Multi-Release: true`.
- Library logging is switched off (`slf4j-nop`, `XRLog`, java.util.logging at `SEVERE`); the tool reports
  problems itself.
- `*IT` tests (Failsafe) run against the finished JAR: help, version, the start script of the current
  system, and a complete run to a valid e-invoice.
