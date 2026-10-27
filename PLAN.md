# e-invoice-generator – Planung

Stand: 27.10.2026 · Status: PoC abgeschlossen, Umsetzung noch nicht begonnen

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
| Sprache | **Java 25** |
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
(Stand 26.10.2026). Damit das so bleibt, PDFBox in der `pom.xml` per `dependencyManagement`
auf eine Version festlegen und den `maven-enforcer-plugin` mit der Regel `dependencyConvergence`
einsetzen, beschränkt auf `org.apache.pdfbox` (siehe 11). Er meldet beim Build, wenn PDFBox-Versionen
auseinanderlaufen. Beides ist seit dem PoC in der `pom.xml` umgesetzt.

picocli wird vorerst **nicht** gebraucht (nur interaktiver Ablauf). Es wird interessant, wenn
später Unterbefehle wie `send` dazukommen.

## 3. Konfiguration

Drei Dateien (Ablageort siehe offene Punkte, Vorschlag: `~/rechnungen/config/`).

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
  phone: ...
outputDir: ~/rechnungen
logo: ~/rechnungen/config/logo.svg       # optional
template: ~/rechnungen/config/invoice.ftlh  # optional, sonst eingebautes Template
```

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
```

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
✔ PDF/A-2b konform, 2 Seiten

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
- Der Stundennachweis wird **sofort nach Eingabe** auf PDF/A-Konformität geprüft (siehe 6).

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
- **Technik-Hinweis openhtmltopdf:** Inhalte außerhalb des Seiteninhalts werden abgeschnitten. Die
  Seitenränder links/rechts sind deshalb als Innenabstand von `body` umgesetzt, damit der Briefkopf im
  oberen Bereich frei positioniert werden kann.

## 6. Stundennachweis

**Entscheidung:** Der Nachweis ist **beides**:
1. **Angehängte Seiten** im Rechnungs-PDF (Kunde sieht ihn sofort, wie bisher).
2. **Eingebettet im XML** als BG-24 „Additional supporting documents“ (Base64-PDF,
   Bezeichnung z. B. „Stundennachweis 10/2026“). So kommt er auch bei Empfängern an,
   die nur das XML verarbeiten.

**Zulässigkeit:** Das XML ist führend. PDF und XML dürfen sich bei den Rechnungsangaben nicht
widersprechen. Zusätzliche Informationen im PDF (Nachweis, AGB …) sind zulässig und gelten
als ergänzende Unterlagen. Die Stundensumme im Nachweis muss zur Rechnung passen.
(Eigene Lesart von ZUGFeRD-Spezifikation und BMF-Schreiben zur E-Rechnung, keine Steuerberatung.)

**Annahme:** Die Nachweise sind PDF/A-konform (werden mit einem PDF/A-Druckertreiber erzeugt).

**Prüfung (zweistufig):**
1. **Nachweis direkt nach Eingabe** mit veraPDF prüfen (direkt, nicht über den ZUGFeRD-Validator,
   der sonst das fehlende XML bemängeln würde). Bei Fehlern die wichtigsten Verstöße anzeigen,
   dann Pfad neu abfragen oder abbrechen. Keine Rechnung erzeugen.
2. **Fertige Rechnung** prüfen (PDF/A-3 + ZUGFeRD-XML), weil auch zwei gültige Dateien zusammengefügt ungültig werden können.

**Zu beachten beim Zusammenfügen:**
- PDF/A-1b/2b-Seiten dürfen in einem PDF/A-3-Dokument stecken.
- Nur **ein Output Intent** (sRGB aus der Rechnung). Ein CMYK-Nachweis würde kollidieren,
  dann wenn möglich den Druckertreiber auf RGB umstellen oder im Code lösen.
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
    │   ├── App.java                      # main, verdrahtet alles
    │   ├── config/                       # Records + YAML-Laden
    │   │   ├── Settings.java, Seller.java, Customer.java, Address.java
    │   │   └── ConfigLoader.java
    │   ├── domain/
    │   │   ├── Invoice.java              # Nummer, Datum, Zeitraum, Kunde, Stunden, Beträge
    │   │   └── InvoiceCalculator.java
    │   ├── numbering/InvoiceCounter.java
    │   ├── pdf/PdfRenderer.java          # FreeMarker → XHTML → PDF/A
    │   ├── pdf/TimesheetValidator.java   # veraPDF-Prüfung des Nachweises
    │   ├── zugferd/
    │   │   ├── ZugferdMapper.java        # Domain → Mustang-Invoice (inkl. BG-24-Anhang)
    │   │   └── HybridInvoiceWriter.java  # PDF/A + Nachweisseiten + XML → PDF/A-3
    │   ├── archive/InvoiceArchive.java   # Dateinamen, Duplikatprüfung, kein Überschreiben
    │   └── cli/InteractiveDialog.java    # JLine
    ├── main/resources/
    │   ├── templates/invoice.ftlh
    │   └── fonts/
    └── test/java/...
```

Grundsatz: `domain` kennt weder PDF noch ZUGFeRD noch die Konsole, damit es gut testbar ist.

### Abhängigkeiten zwischen den Packages

- `domain` hängt von keinem anderen Package ab.
- `config`, `numbering`, `pdf`, `zugferd` und `archive` hängen nur von `domain` ab, nicht voneinander.
- `cli` und `App` kennen alle Packages und verbinden sie miteinander.

### Interfaces (Entwurf)

Interfaces gibt es nur dort, wo mehrere Umsetzungen denkbar sind oder Tests etwas austauschen sollen.
Die Methodensignaturen werden erst nach dem PoC festgelegt (siehe 9).

| Interface | Zweck | Umsetzung (heute) | Weitere/Test-Umsetzung |
|---|---|---|---|
| `InvoiceDocumentRenderer` | Rechnung → sichtbares PDF/A | FreeMarker + openhtmltopdf (`PdfRenderer`) | anderes Layout oder anderer Renderer |
| `EInvoiceWriter` | PDF/A + Rechnung + Nachweis → fertige E-Rechnung | ZUGFeRD via Mustang (`HybridInvoiceWriter`) | später z. B. XRechnung |
| `PdfAValidator` | PDF/A-Prüfung (Stundennachweis) | veraPDF (`TimesheetValidator`) | Stub ohne die langsame Prüfung |
| `EInvoiceValidator` | Prüfung der fertigen E-Rechnung (PDF/A-3 + XML) | Mustang-Validator | Stub |
| `InvoiceNumberSource` | nächste Rechnungsnummer holen und bestätigen | Datei `invoice-counter` (`InvoiceCounter`) | im Speicher |
| `InvoiceArchive` | Ablage, Duplikatprüfung, kein Überschreiben | Dateisystem | im Speicher |

**Keine Interfaces** für die Domänen-Records und den `InvoiceCalculator`: reine Logik mit genau einer
Umsetzung, die sich direkt testen lässt.

**Entscheidung:** „Nachweisseiten anhängen“ und „XML einbetten“ sind **ein** Schritt in `EInvoiceWriter`.
- Die Reihenfolge ist fest: erst Seiten anhängen, dann XML einbetten und PDF/A-3-Metadaten schreiben.
  Würde man danach noch Seiten anhängen, gingen XMP-Metadaten bzw. Konformität kaputt.
- Wie der Nachweis eingebunden wird, ist formatabhängig: ZUGFeRD hängt ihn als Seiten an und bettet
  ihn zusätzlich als BG-24 ins XML ein; eine reine XML-Rechnung (XRechnung) hätte gar keine Seiten.
- Das Anhängen der Seiten ist eine interne Hilfsklasse im Package `zugferd`, kein eigenes Interface.
Der PoC hat bestätigt, dass das Ergebnis die veraPDF-Prüfung (PDF/A-3b) besteht.

## 9. Umsetzungsreihenfolge

1. ✔ **PoC (Proof of Concept)**, erledigt am 27.10.2026 (Ergebnisse siehe 11).
   Als Nächstes die **Methodensignaturen der Interfaces** (siehe 8) festlegen und im Plan ergänzen.
2. Domänenmodell + Laden der YAML-Dateien (+ Tests für die Berechnung).
3. FreeMarker-Beispieltemplate → PDF/A (mit Logo-Unterstützung und HTML-Vorschau).
4. Stundennachweis: veraPDF-Prüfung, Seiten anhängen, BG-24-Einbettung im XML.
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
- [ ] **Java 25** in der Cloud-Umgebung: dort ist nur JDK 21 installiert, und die Download-Quellen für
      JDK 25 sind gesperrt. Der PoC lässt sich mit `-Dmaven.compiler.release=21` bauen (kein Java-25-Code).

## 11. Ergebnisse des PoC (27.10.2026)

Ausführen: `mvn compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.poc.PocMain`
(Ausgabe in `target/poc/`: HTML-Vorschau, Zwischen-PDFs, fertige E-Rechnung, Validierungsbericht).

| Schritt | Ergebnis |
|---|---|
| FreeMarker → XHTML → PDF/A-3b (openhtmltopdf) | ✔ veraPDF: konform (3b) |
| Stundennachweis (Beispiel, PDF/A-2b) mit veraPDF prüfen | ✔ konform (2b) |
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
- **Noch offen:** Ein „echter“ Nachweis aus dem PDF/A-Druckertreiber (evtl. CMYK, anderer Output Intent)
  wurde nicht getestet, nur ein selbst erzeugter.
