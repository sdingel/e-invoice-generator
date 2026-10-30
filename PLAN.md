# e-invoice-generator – Planung

Stand: 30.10.2026 · Status: PoC abgeschlossen, Interfaces festgelegt, Umsetzung noch nicht begonnen

Ein Kommandozeilen-Tool für Solo-Selbständige: erzeugt monatliche Rechnungen als hybride
ZUGFeRD-Rechnung (PDF/A-3 mit eingebettetem XML) inklusive Stundennachweis.

**Name:** `e-invoice-generator` – erzeugt ausschließlich E-Rechnungen (derzeit ZUGFeRD; weitere Formate wie XRechnung später möglich), keine Buchhaltungssoftware. Wer nur ein PDF will, bekommt die ZUGFeRD-Rechnung und ignoriert das XML.

---

## 1. Fachliche Entscheidungen

| Thema | Entscheidung |
|---|---|
| Kunden | Nur deutsche B2B-Kunden, keine öffentlichen Auftraggeber, keine Auslandskunden |
| Profil | ZUGFeRD **EN16931 (Comfort)**, keine XRechnung |
| Umsatzsteuer | Immer **19 %**, Steuerkategorie `S` (kein Kleinunternehmer, kein Reverse Charge) |
| Abrechnungsrhythmus | Monatsweise, Leistungszeitraum standardmäßig = Vormonat (im Dialog überschreibbar) |
| Positionen | Genau **eine Position** pro Rechnung: Menge = Stunden, Einheit `HUR`, Preis = Stundensatz des Kunden |
| Positionstext | Fester Text pro Kunde aus YAML (ohne Monat) |
| Leistungszeitraum | Pflichtangabe (§14 UStG): Spalte in der Positionstabelle + `BillingPeriod` im XML, automatisch berechnet |
| Rechnungsnummer | Rein numerisch, fortlaufend über Jahresgrenzen (z. B. `302`) |
| Zahlungsziel | In Tagen, pro Kunde in YAML; Fälligkeit = Rechnungsdatum + Zahlungsziel |
| Zahlungsart | SEPA-Überweisung (UNTDID 4461 Code `58`) mit IBAN/BIC |
| Beträge | `BigDecimal`, kaufmännisch auf 2 Nachkommastellen; Stunden mit Nachkommastellen erlaubt |
| Pflichtangabe Verkäufer | USt-IdNr. **oder** Steuernummer (eins davon ist Pflicht) |

## 2. Technischer Stack

| Bereich | Wahl |
|---|---|
| Sprache | **Java 21** (LTS; in der Cloud-Umgebung ist nur JDK 21 verfügbar) |
| Build | **Maven**, ausführbares Fat-JAR via `maven-shade-plugin` |
| ZUGFeRD | **Mustangproject** (`org.mustangproject:library`), Validierung mit Mustangs Validator |
| PDF-Layout | **FreeMarker** (`org.freemarker:freemarker`, neueste Version, derzeit 2.3.35; XHTML-Template) + **openhtmltopdf** (Fork `io.github.openhtmltopdf`) → PDF/A |
| SVG-Logo | `openhtmltopdf-svg-support` (Batik) |
| PDF-Manipulation | **Apache PDFBox** (Seiten des Stundennachweises anhängen) |
| PDF/A-Prüfung | **veraPDF** als Java-Bibliothek (kommt über `org.mustangproject:validator` mit) |
| Konfiguration | **Jackson** + `jackson-dataformat-yaml`, Java-Records (gegenüber SnakeYAML direkt bevorzugt: Records ohne eigenen Abbildungscode, `FAIL_ON_UNKNOWN_PROPERTIES` erkennt Tippfehler, Fehlermeldungen mit Pfad) |
| Konsole | **JLine 3** (Zeilenbearbeitung, Tab-Vervollständigung für Kunden und Dateipfade) |
| Tests | JUnit 5, AssertJ |
| Später: Mail | Jakarta Mail (Eclipse Angus Mail) |

**PDFBox-Version:** Mustang 2.26.0 und openhtmltopdf 1.1.100 nutzen beide PDFBox **3.0.8**
(Stand 26.10.2026). PDFBox ist eine **direkte Abhängigkeit** (wir nutzen die API selbst) und legt
damit die Version auch für Mustang und openhtmltopdf fest. Der `maven-enforcer-plugin` prüft beim Build:
- `requireMavenVersion` (≥ 3.9) und `requireJavaVersion` (≥ 21)
- `requireUpperBoundDeps`: keine Bibliothek in einer älteren Version als irgendwo verlangt.
  Hat `commons-logging` 1.3.0 (über Mustang → FOP) statt der von PDFBox verlangten 1.4.0 gefunden;
  das ist der einzige Eintrag im `dependencyManagement`.
- `dependencyConvergence`, beschränkt auf `org.apache.pdfbox` (siehe 11).

picocli wird vorerst **nicht** gebraucht (nur interaktiver Ablauf). Es wird interessant, wenn
später Unterbefehle wie `send` dazukommen.

## 3. Konfiguration

Drei Dateien (Ablageort siehe offene Punkte, Vorschlag: `~/rechnungen/config/`). Kommentierte Beispiele
liegen in `examples/config/`; ein Test stellt sicher, dass sie gültig bleiben.

### `settings.yaml`

```yaml
seller:
  name: Steffen Dingel
  address:
    street: ...
    zip: "....."          # PLZ immer in Anführungszeichen (führende Null!)
    city: ...
    country: DE
  vatId: DE...            # und/oder taxNumber
  taxNumber: ...
  iban: DE...
  bic: ...
  email: ...
outputDir: ~/rechnungen
logo: ~/rechnungen/config/logo.svg       # optional
template: ~/rechnungen/config/invoice.ftlh  # optional, sonst eingebautes Template
```

`settings.yaml` enthält nur Verkäuferdaten, die auch ins **ZUGFeRD-XML** müssen (Name, Anschrift,
USt-IdNr./Steuernummer, IBAN/BIC, E-Mail). Reine Anzeige-Angaben wie Tätigkeitsbezeichnung, Telefon,
Mobil, Website und Name der Bank stehen **fest im Template**. Eigene Templates pro Kunde sind
vorerst nicht vorgesehen, aber später möglich.

### `customers.yaml`

```yaml
customers:
  - id: acme
    name: ACME GmbH
    address:
      street: Musterstraße 1
      zip: "12345"
      city: Musterstadt
      country: DE
    vatId: DE...                      # optional
    email: buchhaltung@acme.example   # schon jetzt anlegen, für späteren Mailversand
    hourlyRate: 95.00
    paymentTermDays: 30
    itemText: Beratung und Softwareentwicklung
    fields:                           # optional: freie Platzhalter für das Template
      vertragsdatum: "15.01.2026"
      ansprechpartner: "z. Hd. Erika Musterfrau"
```

**Freie Platzhalter (`fields`):** beliebige Schlüssel-Wert-Paare (Texte), die das Template als
`customer.fields.<schlüssel>` verwendet. Fehlt ein Wert, reagiert das Template darauf, z. B.
`<#if customer.fields.vertragsdatum??>gemäß unserem Vertrag vom ${customer.fields.vertragsdatum} …<#else>hiermit …</#if>`
oder kurz `${customer.fields.ansprechpartner!}` (gibt bei fehlendem Wert nichts aus). Die Werte
werden unverändert übernommen, also z. B. Datumsangaben schon im gewünschten Format eintragen.
Sie landen nur im PDF, nicht im XML.

### `invoice-counter`

Enthält nur die **nächste** Rechnungsnummer, z. B. `302`.
- Erst erhöhen, wenn die Rechnung erfolgreich geschrieben und validiert ist.
- Atomar schreiben (temporäre Datei, dann umbenennen).

## 4. Bedienung: interaktiver Dialog

```
Kunde:
  1) acme     ACME GmbH
  2) globex   Globex AG
Auswahl [1]: 1

Leistungszeitraum [10/2026]: ⏎
Stunden: 37,5
Stundennachweis (PDF): ~/Downloads/nachweis_okt<TAB>
✔ 2 Seiten, lässt sich PDF/A-konform anhängen

──────────────────────────────────────────
Rechnung Nr. 302 an ACME GmbH
Leistungszeitraum   01.10.2026 – 31.10.2026
37,5 h × 95,00 €    =  3.562,50 €
zzgl. 19 % USt      =    676,88 €
Gesamt              =  4.239,38 €
Fällig am           24.11.2026
──────────────────────────────────────────
Rechnung erzeugen? [J/n]: J

✔ ~/rechnungen/302_acme_2026-10.pdf (validiert)
```

- Stunden: Komma und Punkt als Dezimaltrenner akzeptieren.
- Vor dem Schreiben immer eine Zusammenfassung mit Bestätigung.
- Der Stundennachweis wird **sofort nach Eingabe** probeweise angehängt und geprüft (siehe 6).

## 5. Rechnungs-PDF und Layout

- **Layout** nach der eigenen Word-Vorlage nachgebaut (seit dem PoC, `templates/invoice.ftlh`), mit
  Beispieldaten statt echter Daten (das Repository ist öffentlich). Ohne Falzmarken.
- **Template austauschbar:** eingebautes Template im JAR (`templates/invoice.ftlh`), optional
  überschreibbar per `template:` in `settings.yaml`, also ohne neuen Build.
- **FreeMarker-Konfiguration** (am 27.10.2026 mit 2.3.35 getestet):
  - `new Configuration(Configuration.VERSION_2_3_35)`: Erst ab `incompatible_improvements` 2.3.33
    lassen sich Record-Felder als `${customer.name}` lesen; mit 2.3.32 schlägt das fehl.
  - Templates mit Endung `.ftlh` und `setRecognizeStandardFileExtensions(true)`: Dann maskiert
    FreeMarker `&`, `<`, `>` automatisch (z. B. „Müller &amp; Söhne“). Sonst entsteht ungültiges
    XHTML und openhtmltopdf bricht ab.
  - Beträge und Datumsangaben werden in Java formatiert (deutsches Format), nicht im Template.
- **Vorschau:** Anders als bei Thymeleaf lässt sich ein FreeMarker-Template nicht ungefüllt im Browser
  ansehen. Für die Layout-Arbeit gibt es deshalb eine Vorschau, die das Template mit Beispieldaten als
  HTML ausgibt.
- **Logo:** optional per `logo:` in `settings.yaml`; das Template lässt den Bereich weg, wenn keins gesetzt ist (`<#if logo??>`).
  - Bevorzugt SVG, sonst PNG mit ca. 300 dpi; Transparenz ist in PDF/A-3 erlaubt.
  - Aus Word: `.docx` ist ein ZIP, Bilder liegen unter `word/media/` (oft nur verkleinert, Original besser).
  - Der Renderer bekommt das Konfigurationsverzeichnis als Basis-URI, damit relative Pfade funktionieren.
- Inhalt des Layouts:
  - A4, Ränder wie in Word (links 2,5 cm, rechts 2 cm), Fußzeile auf jeder Seite
  - Kopf oben rechts: Logo + Absender (Name, Tätigkeitsbezeichnung, Anschrift)
  - Anschriftfeld mit Rücksendeangabe
  - Rechnungsdatum, Rechnungsnummer, Anrede, Einleitungssatz (optional mit Vertragsdatum)
  - Positionstabelle: Leistung, Leistungszeitraum, Einheit, Menge, Einzelpreis, Gesamtpreis;
    darunter Summe (netto), Umsatzsteuer 19 %, Rechnungsbetrag
  - Zahlungshinweis („Bitte überweisen Sie den Rechnungsbetrag bis zum … auf das unten angegebene Konto.“)
  - Anlagen („Stundennachweis MM/JJJJ“)
  - Fußzeile in drei Spalten: Anschrift + USt-IdNr. | Telefon, Mobil, E-Mail, Web | Bankverbindung
- **Schrift: Carlito** (Regular, Bold, Italic, BoldItalic als TTF in `src/main/resources/fonts/`, mit
  Lizenz `OFL-Carlito.txt`). Carlito ist maßgleich zu Calibri aus der Word-Vorlage, steht aber unter
  der SIL Open Font License und darf daher eingebettet und weitergegeben werden (Calibri nicht).
- **Schutz gegen leere Rechnungen:** Fehlt openhtmltopdf eine Schrift, lässt es den Text im PDF/A-Modus
  **stillschweigend weg** (nur Warnung „Font list is empty“). veraPDF meldet die leere Seite trotzdem
  als konform (am 27.10.2026 getestet). Deshalb:
  - Beim Start prüfen, dass alle Schriftdateien vorhanden und lesbar sind; sonst mit Fehlermeldung abbrechen.
  - Nach dem Rendern den Text aus dem PDF zurücklesen (PDFBox `PDFTextStripper`) und prüfen, dass
    mindestens Rechnungsnummer und Rechnungsbetrag darin vorkommen; sonst abbrechen.
  - Ohne PDF/A-Modus würde openhtmltopdf auf eine nicht eingebettete Standardschrift (Times-Roman)
    ausweichen, die Zeichen wie „Ł“ oder „≥“ nicht kennt (`#`). Systemschriften nutzt es nicht von selbst.
- **Technik-Hinweis openhtmltopdf:** Inhalte außerhalb des Seiteninhalts werden abgeschnitten. Die
  Seitenränder links/rechts sind deshalb als Innenabstand von `body` umgesetzt, damit der Briefkopf im
  oberen Bereich frei positioniert werden kann.

## 6. Stundennachweis

**Entscheidung:** Der Nachweis ist **beides**:
1. **Angehängte Seiten** im Rechnungs-PDF (Kunde sieht ihn sofort, wie bisher).
2. **Eingebettet im XML** als BG-24 „Additional supporting documents“ (Base64-PDF,
   Bezeichnung z. B. „Stundennachweis 10/2026“). So kommt er auch bei Empfängern an,
   die nur das XML verarbeiten.

Am 29.10.2026 bestätigt, obwohl der Nachweis dadurch zweimal in der Datei steckt (im PoC: 26 KB
Nachweis → XML 41 KB, Datei 100 KB statt 53 KB). Grund: Wie die Kunden die Rechnungen verarbeiten,
ist unbekannt. Verworfene Alternativen: nur sichtbare Seiten; BG-24 nur als Verweis ohne Inhalt;
Nachweis als eigene Datei im PDF/A-3-Container. Bei Bedarf später pro Kunde einstellbar machen.

**Zulässigkeit:** Das XML ist führend. PDF und XML dürfen sich bei den Rechnungsangaben nicht
widersprechen. Zusätzliche Informationen im PDF (Nachweis, AGB …) sind zulässig und gelten
als ergänzende Unterlagen. Die Stundensumme im Nachweis muss zur Rechnung passen.
(Eigene Lesart von ZUGFeRD-Spezifikation und BMF-Schreiben zur E-Rechnung, keine Steuerberatung.)

**Der Nachweis muss selbst kein PDF/A sein.** (Geändert am 27.10.2026.) Ein Test mit einem
Excel-Export (PDF 1.7, Schriften eingebettet, aber ohne PDF/A-Kennzeichnung und ohne Farbprofil) zeigte:
Allein geprüft ist er kein PDF/A, an die Rechnung angehängt ergibt er aber gültiges **PDF/A-3b**,
weil Farbprofil (Output Intent) und Kennzeichnung der Rechnung für das ganze Dokument gelten.
PDF/A verlangt ZUGFeRD nur für die fertige Rechnung. Ein Nachweis als PDF/A (z. B. PDF/A-2b über
den Druckertreiber PDFCreator, ebenfalls getestet) ist trotzdem empfehlenswert: Die im XML
eingebettete Kopie (BG-24) ist dann selbst archivtauglich, und Export-Probleme fallen früher auf. Die frühere Annahme „Nachweise kommen aus
einem PDF/A-Druckertreiber“ entfällt; Excel- und vermutlich auch Jira/Tempo-Exporte funktionieren so.

**Prüfung (zweistufig):**
1. **Nachweis direkt nach Eingabe** probeweise an eine leere PDF/A-3b-Seite (bzw. das gerenderte
   Rechnungs-PDF) anhängen und das Ergebnis mit veraPDF prüfen. Bei Fehlern die wichtigsten
   Verstöße anzeigen, dann Pfad neu abfragen oder abbrechen. Keine Rechnung erzeugen.
   Typische Ursachen: nicht eingebettete Schriften, CMYK-Farben, Verschlüsselung.
2. **Fertige Rechnung** prüfen (PDF/A-3 + ZUGFeRD-XML), weil auch zwei gültige Dateien zusammengefügt ungültig werden können.

**Zu beachten beim Zusammenfügen:**
- PDF/A-1b/2b-Seiten dürfen in einem PDF/A-3-Dokument stecken.
- Nur **ein Output Intent** (sRGB aus der Rechnung). Ein CMYK-Nachweis würde kollidieren,
  dann wenn möglich den Export auf RGB umstellen oder im Code lösen.
- Jira/Tempo-Exporte konnten nicht getestet werden (vertrauliche Daten); das testet der Nutzer selbst.
- XMP-Metadaten des Nachweises verwerfen; die Metadaten der Rechnung gelten für das ganze Dokument.

**Plan B (vorerst nicht gebaut):** Nachweisseiten mit PDFBox als Bild rendern (300 dpi) und als
Bildseiten einfügen. Das ist immer PDF/A-konform, aber nicht mehr durchsuchbar. Nur nachrüsten, falls nötig.

## 7. Ablage

- Fertige Rechnungen in `outputDir` (nicht mit Git versioniert).
- Dateiname: `<Nummer>_<kundenId>_<JJJJ-MM>.pdf`, z. B. `302_acme_2026-10.pdf`.
- Vorhandene Dateien **nie überschreiben**.
- Warnung bei Doppelabrechnung (gleicher Kunde und Monat existiert schon).
- Erzeugte Rechnungen sind unveränderlich und werden nie aus der aktuellen Konfiguration neu erzeugt.

## 8. Projektstruktur (Vorschlag)

```
e-invoice-generator/
├── pom.xml
└── src/
    ├── main/java/de/steffendingel/einvoicegenerator/
    │   ├── App                               # main: Konfiguration laden, Umsetzungen erzeugen, Dialog starten
    │   ├── InvoiceService                    # Ablauf ①–⑪, kennt nur die Interfaces
    │   ├── InvoiceCalculator                 # ① Berechnung (reine Logik, ohne Abhängigkeiten)
    │   ├── model/                            # Datenklassen (Records), keine Logik
    │   │   └── Address, Seller, Customer, BillingPeriod, InvoiceRequest, Invoice,
    │   │       Timesheet, ValidationResult
    │   ├── config/
    │   │   ├── Settings                      # Ausgabeordner, Template, Logo (reine Konfiguration)
    │   │   └── ConfigLoader                  # YAML → Settings, Seller, Customer (Jackson, direkt auf die Records)
    │   ├── format/
    │   │   └── GermanFormat                  # Beträge/Datum im deutschen Format (für ② und ④)
    │   ├── html/
    │   │   └── InvoiceHtmlRenderer + FreemarkerHtmlRenderer          # ②
    │   ├── pdf/
    │   │   ├── HtmlToPdfConverter + OpenHtmlToPdfConverter           # ③
    │   │   ├── PdfTextVerifier + PdfBoxTextVerifier                  # ④
    │   │   ├── PdfPageAppender + PdfBoxPageAppender                  # ⑤
    │   │   └── PdfAValidator + VeraPdfValidator                      # ⑥
    │   ├── einvoice/                         # formatneutrale Interfaces, ZUGFeRD-Umsetzungen (später XRechnung)
    │   │   ├── EInvoiceXmlGenerator + ZugferdXmlGenerator            # ⑦ Invoice → Mustang → CII-XML (inkl. BG-24)
    │   │   ├── EInvoiceXmlEmbedder + ZugferdXmlEmbedder              # ⑧
    │   │   └── EInvoiceValidator + MustangValidator                  # ⑨
    │   ├── archive/
    │   │   └── InvoiceArchive + FileInvoiceArchive                   # ⑩ Dateinamen, Duplikatprüfung, kein Überschreiben
    │   ├── numbering/
    │   │   └── InvoiceNumberSource + FileInvoiceNumberSource         # ① Nummer holen, ⑪ hochzählen
    │   └── cli/InteractiveDialog             # JLine
    ├── main/resources/
    │   ├── templates/invoice.ftlh
    │   └── fonts/
    └── test/java/...
```

Grundsatz: **Jedes Interface liegt bei seiner Umsetzung** (geändert am 30.10.2026, vorher lagen alle
Interfaces in einem `domain`-Package). Alles zu einem Thema, z. B. PDF, steht so in einem Package.
`Seller` und `Customer` sind Fachbegriffe und liegen deshalb in `model`, obwohl sie aus der
Konfiguration gelesen werden; sie gibt es nur einmal. In `config` liegt nur reine Konfiguration.
Der **Kern liegt im obersten Package** (`de.steffendingel.einvoicegenerator`): `InvoiceService` steuert den
Ablauf, `InvoiceCalculator` rechnet, `App` verdrahtet. `App` und `InvoiceService` bleiben getrennt, damit
der Ablauf mit Ersatz-Umsetzungen (z. B. Archiv im Speicher) testbar ist; `InvoiceCalculator` bleibt eine
eigene Klasse, damit die Berechnung (Rundung, Fälligkeit) ohne weitere Abhängigkeiten testbar ist.

### Abhängigkeiten zwischen den Packages

- `model` hängt von keinem anderen Package ab.
- `format` hängt nur von `model` ab.
- `config`, `html`, `pdf`, `einvoice`, `archive` und `numbering` hängen nur von `model` (und `html` von
  `format`) ab, nicht voneinander.
- Im obersten Package gilt die Regel pro Klasse: `InvoiceService` und `InvoiceCalculator` kennen `model`,
  `format` und die **Interfaces** der Schritt-Packages, nicht deren Umsetzungen.
- `cli` und `App` kennen alle Packages und verbinden sie miteinander.

### Ablauf, Schritte und Interfaces (überarbeitet am 29.10.2026)

Jeder Verarbeitungsschritt im Diagramm der README hat eine Nummer und genau ein Interface mit klar
definierter Eingabe und Ausgabe. Die Schritte lassen sich einzeln umsetzen und testen. Interface und
Umsetzung liegen jeweils im selben Package (siehe Projektstruktur).

| Nr. | Schritt | Interface (Umsetzung) | Eingabe | Ausgabe |
|---|---|---|---|---|
| ① | Calculate invoice | – (Klasse `InvoiceCalculator`) | `InvoiceRequest`, Rechnungsnummer | `Invoice` |
| ② | Fill template | `InvoiceHtmlRenderer` (FreeMarker) | `Invoice`, Bezeichnungen der Anlagen | XHTML (`String`) |
| ③ | Render to PDF/A | `HtmlToPdfConverter` (openhtmltopdf) | XHTML | Rechnungs-PDF (PDF/A-3b) |
| ④ | Read text back | `PdfTextVerifier` (PDFBox) | PDF, erwartete Texte | `ValidationResult` |
| ⑤ | Append timesheet pages | `PdfPageAppender` (PDFBox) | Rechnungs-PDF, Nachweis-PDFs | zusammengefügtes PDF |
| ⑥ | Check PDF/A | `PdfAValidator` (veraPDF) | PDF | `ValidationResult` |
| ⑦ | Create XML | `EInvoiceXmlGenerator` (Mustang) | `Invoice`, Nachweise | CII-XML (EN16931, Nachweise als BG-24) |
| ⑧ | Embed XML | `EInvoiceXmlEmbedder` (Mustang) | zusammengefügtes PDF, XML | hybride E-Rechnung (PDF/A-3b) |
| ⑨ | Validate e-invoice | `EInvoiceValidator` (Mustang-Validator) | E-Rechnung | `ValidationResult` |
| ⑩ | Store invoice | `InvoiceArchive` (Dateisystem) | `Invoice`, E-Rechnung | Ablageort |
| ⑪ | Increment invoice number | `InvoiceNumberSource` (Datei) | verwendete Nummer | – |

Die Rechnungsnummer für ① liefert ebenfalls `InvoiceNumberSource` (`peekNext`); verbraucht wird sie
erst in ⑪, damit bei einem Abbruch keine Nummer verloren geht.

**Prüfung des Nachweises direkt nach der Eingabe** (siehe 6): `InvoiceService` führt ⑤ und ⑥ probeweise
aus (Nachweis an eine leere PDF/A-3b-Seite anhängen und prüfen). Dafür braucht es kein eigenes Interface.

**XML erzeugen ⑦ und einbetten ⑧ sind getrennt.** Am 29.10.2026 im PoC bestätigt: Mustang erzeugt das XML
separat (`ZUGFeRD2PullProvider`) und bettet ein fertiges XML ein (`setXML`); das eingebettete XML ist
Byte für Byte identisch, das Ergebnis weiterhin gültig. Die Trennung ist bewusst gewählt (statt eines
gemeinsamen Schritts wie im üblichen Mustang-Aufruf): ⑦ ist ohne PDF testbar (z. B. Datumsangaben im
XML, siehe Datumsfalle in 11), und eine spätere XRechnung braucht nur ⑦. Das ZUGFeRD-Profil (EN16931)
steht als eine gemeinsame Konstante für ⑦ und ⑧.

**Reihenfolge:** Seiten anhängen ⑤ muss vor dem Einbetten ⑧ passieren, sonst gehen XMP-Metadaten und
Konformität kaputt. Das stellt `InvoiceService` sicher.

**Andere Formate (z. B. XRechnung, später):** reine XML-Rechnungen brauchen nur ① und ⑦, ⑨–⑪
(mit eigener Umsetzung von ⑦ und ⑨); ②–⑥ und ⑧ entfallen.

### Signaturen

Datenklassen in `model` (Records, Werte unformatiert):

```java
record Address(String street, String zip, String city, String country) {}

record Seller(String name, Address address, String vatId, String taxNumber,
              String iban, String bic, String email) {}       // nur XML-relevante Daten, siehe 3

record Customer(String id, String name, Address address, String vatId, String email,
                BigDecimal hourlyRate, int paymentTermDays, String itemText,
                Map<String, String> fields) {}                 // fields: freie Platzhalter, siehe 3

record BillingPeriod(LocalDate from, LocalDate to) {
    static BillingPeriod of(YearMonth month) { ... }          // Standardfall: ganzer Monat
}

/** Eingaben aus dem Dialog. */
record InvoiceRequest(Customer customer, BillingPeriod period, BigDecimal hours, LocalDate invoiceDate) {}

/** Fertig berechnete Rechnung, Grundlage für PDF und XML. */
record Invoice(String number, LocalDate invoiceDate, LocalDate dueDate, BillingPeriod period,
               Seller seller, Customer customer, BigDecimal hours, BigDecimal hourlyRate,
               BigDecimal netAmount, BigDecimal vatRate, BigDecimal vatAmount, BigDecimal grossAmount) {}

/** Stundennachweis; label erscheint im PDF („Anlagen“) und im XML (BG-24). */
record Timesheet(String fileName, String label, byte[] pdf) {}

record ValidationResult(boolean valid, String summary, List<String> problems) {}
```

Konfiguration in `config`:

```java
record Settings(Seller seller, Path outputDir, Path logo, Path template) {}   // logo, template optional
```

Interfaces (Nummern wie im Diagramm, Package jeweils dabei):

```java
// ② html
interface InvoiceHtmlRenderer {
    String renderHtml(Invoice invoice, List<String> attachmentLabels) throws IOException;
}

// ③ pdf
interface HtmlToPdfConverter {
    /** Converts XHTML into a PDF/A-3b document with embedded fonts. */
    byte[] convert(String xhtml) throws IOException;
}

// ④ pdf
interface PdfTextVerifier {
    /** Checks that all expected texts occur in the PDF (guards against silently missing fonts). */
    ValidationResult verify(byte[] pdf, List<String> expectedTexts) throws IOException;
}

// ⑤ pdf
interface PdfPageAppender {
    /** Appends all pages of the attachments; metadata and output intent of the base document are kept. */
    byte[] append(byte[] basePdf, List<byte[]> attachmentPdfs) throws IOException;
}

// ⑥ pdf
interface PdfAValidator {
    ValidationResult validate(byte[] pdf) throws IOException;
}

// ⑦ einvoice
interface EInvoiceXmlGenerator {
    byte[] generateXml(Invoice invoice, List<Timesheet> timesheets) throws IOException;
}

// ⑧ einvoice
interface EInvoiceXmlEmbedder {
    /** Embeds the XML into the PDF/A document and writes the PDF/A-3b and e-invoice metadata. */
    byte[] embed(byte[] pdf, byte[] xml) throws IOException;
}

// ⑨ einvoice
interface EInvoiceValidator {
    ValidationResult validate(byte[] eInvoice) throws IOException;
}

// ⑩ archive
interface InvoiceArchive {
    /** Existing invoice for the same customer and period (warning about double billing). */
    Optional<String> findExisting(String customerId, BillingPeriod period) throws IOException;

    /** Stores the invoice and returns where it was stored; never overwrites an existing file. */
    String store(Invoice invoice, byte[] eInvoice) throws IOException;
}

// ① (Nummer holen) und ⑪ numbering
interface InvoiceNumberSource {
    /** Next free number, without reserving it. */
    String peekNext() throws IOException;

    /** Marks the number as used; fails if it is not the expected next number. */
    void markUsed(String number) throws IOException;
}
```

Ablauf in `InvoiceService` (oberstes Package, kennt nur die Interfaces):

```java
ValidationResult checkTimesheet(Timesheet timesheet);        // ⑤ + ⑥ probeweise, direkt nach der Eingabe
Optional<String> findExisting(Customer customer, BillingPeriod period);
Invoice prepare(InvoiceRequest request);                       // peekNext + ① → Zusammenfassung im Dialog
String generate(Invoice invoice, List<Timesheet> timesheets);  // nach Bestätigung: ② → ③ → ④ → ⑤ → ⑥
                                                               // → ⑦ → ⑧ → ⑨ → ⑩ → ⑪; jede Prüfung bricht ab
```

**Begründungen:**
- **`byte[]` statt Dateien:** Rechnung und Nachweise sind klein (einige 100 KB); im Speicher sind die
  Schritte einfacher zu verketten und zu testen. Nur `InvoiceArchive`, `InvoiceNumberSource` und
  `ConfigLoader` arbeiten mit Dateien.
- **Rechnungsnummer als `String`,** weil sie so ins XML und in den Dateinamen geht; intern zählt
  `InvoiceNumberSource` rein numerisch.
- **Erwartete Texte für ④** (Rechnungsnummer, Rechnungsbetrag) erzeugt `InvoiceService` mit
  `GermanFormat`, also im selben Format wie im Template.
- **HTML-Vorschau** ist einfach das Ergebnis von ②.
- **Seller/Customer:** Jackson liest die YAML-Dateien direkt in diese Records (Namen passen, keine
  Annotationen nötig).
- **Fehler:** Technische Fehler als `IOException`, Prüfergebnisse als `ValidationResult`.

## 9. Umsetzungsreihenfolge

1. ✔ **PoC (Proof of Concept)**, erledigt am 27.10.2026 (Ergebnisse siehe 11).
   ✔ **Methodensignaturen der Interfaces** festgelegt am 29.10.2026 (siehe 8).
2. Domänenmodell + Laden der YAML-Dateien (+ Tests für die Berechnung).
   - ✔ Datenklassen in `model`, ① `InvoiceCalculator`, `GermanFormat`, mit Tests (30.10.2026).
     Festgelegt: Rechnungsdatum standardmäßig heute; Leistungszeitraum standardmäßig der Vormonat des
     Rechnungsdatums; Fälligkeit = Rechnungsdatum + Zahlungsziel, ohne Verschiebung auf einen Werktag.
   - ✔ Konfiguration: `Settings`, `ConfigLoader` (YAML) mit Tests, Beispieldateien in `examples/config/`
     (30.10.2026). Unbekannte Schlüssel (Tippfehler), fehlende Zahlen und Texte ohne Anführungszeichen,
     die YAML als Zahl liest (PLZ mit führender Null), werden mit Pfad gemeldet. Ein einzelnes `~` ist in
     YAML „leer“; das Home-Verzeichnis daher als `~/…` schreiben.
3. FreeMarker-Beispieltemplate → PDF/A (mit Logo-Unterstützung und HTML-Vorschau).
4. Stundennachweis: probeweise anhängen und mit veraPDF prüfen, Seiten anhängen, BG-24-Einbettung im XML.
5. Interaktiver Dialog (JLine), Zähler, Ablage, Abschlussvalidierung.

## 10. Offene Punkte / Risiken

- [x] **PDFBox-Versionskonflikt:** geklärt am 26.10.2026 anhand der POMs auf Maven Central, kein Konflikt.
      `org.mustangproject:library` 2.26.0 und `io.github.openhtmltopdf:openhtmltopdf-pdfbox` 1.1.100
      nutzen beide PDFBox 3.0.8. Nur das alte `com.openhtmltopdf` (zuletzt 1.0.10, 2021) nutzt
      noch PDFBox 2, daher den Fork verwenden. Absicherung siehe Abschnitt 2.
- [x] **veraPDF-Artefakte:** kommen transitiv über `org.mustangproject:validator` (2.26.0) mit
      (`org.verapdf:validation-model-jakarta`), nicht über `library`. Kein eigenes veraPDF-Artefakt nötig.
- [ ] **Aktuelle Versionen** aller Abhängigkeiten beim Projektstart festlegen.
- [ ] **Ablageort der Konfiguration** festlegen (Vorschlag: `~/rechnungen/config/`).
- [x] **Package-Name:** `de.steffendingel.einvoicegenerator` (Schema `de.steffendingel.<projektname>`, eigene Domain `steffendingel.de`).
- [x] **Schrift:** Carlito (maßgleich zu Calibri, OFL), siehe 5.
- [x] **Eigenes Layout** aus Word in HTML/CSS nachgebaut (im PoC).
- [ ] **Logo** bereitstellen (bevorzugt SVG); im Template derzeit ein Platzhalter.
- [ ] **Mailversand** *(später)*: Jakarta Mail/Angus Mail via SMTP, an Kunden-`email`,
      BCC an das DMS; SMTP-Zugangsdaten **nicht** in der YAML, sondern in Umgebungsvariablen
      oder einer separaten, geschützten Datei; zweistufig (erst erzeugen und ansehen, dann eigener
      Sende-Befehl → dann ggf. picocli für Unterbefehle).
- [ ] **Plan B Stundennachweis** (als Bild rendern) nur bei Bedarf.
- [x] **Java-Version:** Java 21 statt 25 (in der Cloud-Umgebung ist nur JDK 21 verfügbar).

## 11. Ergebnisse des PoC (27.10.2026)

Ausführen: `mvn compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.poc.PocMain`
(optional `-Dexec.args="<ausgabeordner> <nachweis.pdf>"` für einen eigenen Stundennachweis)
(Ausgabe in `target/poc/`: HTML-Vorschau, Zwischen-PDFs, fertige E-Rechnung, Validierungsbericht).

| Schritt | Ergebnis |
|---|---|
| FreeMarker → XHTML → PDF/A-3b (openhtmltopdf) | ✔ veraPDF: konform (3b) |
| Stundennachweis (Beispiel, PDF/A-2b) mit veraPDF prüfen | ✔ konform (2b) |
| Excel-Nachweis (kein PDF/A) angehängt | ✔ Ergebnis konform (3b), siehe 6 |
| Nachweis via PDFCreator (PDF/A-2b, Calibri eingebettet) angehängt | ✔ Nachweis allein 2b, Ergebnis 3b, Validator „valid“ |
| Nachweisseiten mit PDFBox anhängen (`importPage`) | ✔ weiterhin konform (3b) |
| ZUGFeRD EN16931 mit Mustang, Nachweis zusätzlich als BG-24 (`AdditionalReferencedDocument`, TypeCode 916) | ✔ |
| Fertige E-Rechnung: veraPDF + Mustang-Validator (Schema, Schematron) | ✔ konform (3b), „valid“ |
| `mvn dependency:tree` | ✔ nur PDFBox 3.0.8 |

**Erkenntnisse, die in die Umsetzung gehören:**
- **Konformitätsstufe:** Mustang schreibt standardmäßig PDF/A-3**u** in die Metadaten. Das schlug in
  veraPDF fehl (Regel 6.2.11.7.2, fehlende Unicode-Zuordnung einzelner Zeichen), obwohl Mustangs eigener
  Validator „valid“ meldete. Lösung: `setConformanceLevel(PDFAConformanceLevel.BASIC)` → 3b. Das ist
  auch robuster für Nachweise aus PDF/A-Druckertreibern.
- **Datumsfalle:** Mustang nimmt `java.util.Date` und formatiert in der Zeitzone der JVM. Mitternacht in
  einer anderen Zone (z. B. `Europe/Berlin` auf einer UTC-Maschine) verschiebt alle Daten im XML um
  einen Tag, ohne dass ein Validator es merkt. Umrechnung daher mit `ZoneId.systemDefault()`; ein Test
  soll die Datumsangaben im XML gegen die erwarteten Werte prüfen.
- **Farbprofil:** Das sRGB-Profil für PDF/A kommt aus dem JDK
  (`ICC_Profile.getInstance(ColorSpace.CS_sRGB)`), keine eigene ICC-Datei nötig.
- **Summen:** Mustang rechnet die Summen selbst; sie stimmen mit unserer Berechnung
  (`BigDecimal`, kaufmännisch gerundet) überein: 37,5 h × 95,00 € = 3.562,50 €, USt 676,88 €, brutto 4.239,38 €.
- **Hinweise des Validators:** nur „notice“-Meldungen aus den XRechnung-Regeln (Käuferreferenz,
  elektronische Adresse usw.). Für ZUGFeRD EN16931 sind das keine Fehler.
- **Enforcer:** `dependencyConvergence` für alle Abhängigkeiten scheitert an Mustangs eigenem Baum
  (slf4j, ph-commons, Saxon u. a. in leicht abweichenden Versionen). Die Regel ist daher auf
  `org.apache.pdfbox` beschränkt; mit einer absichtlich falschen PDFBox-Version schlägt sie an.
  Für alle Abhängigkeiten deckt `requireUpperBoundDeps` den gefährlicheren Fall ab (Version zu alt).
- **Nachweis ohne PDF/A:** Ein echter Excel-Export lässt sich anhängen, das Ergebnis ist PDF/A-3b
  (siehe 6). Jira/Tempo-Exporte testet der Nutzer selbst.
- **Fehlende Schriften** führen still zu leeren Seiten (siehe 5, „Schutz gegen leere Rechnungen“).
- Im PoC-Template stehen die Anzeige-Angaben des Verkäufers noch als Variablen; in Schritt 3 werden
  sie fest ins Template geschrieben (siehe 3).
