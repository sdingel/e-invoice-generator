# e-invoice-generator

Command-line tool that generates hybrid ZUGFeRD e-invoices (PDF/A-3 with embedded XML) including a
timesheet. The plan and all decisions are in `PLAN.md`.

## Language

- **Everything in the repository is English:** code, identifiers, comments, Javadoc, documentation,
  commit messages, pull requests and issues.
- **Exceptions:**
  - `PLAN.md` stays German.
  - Comments in the throwaway proof of concept (`de.steffendingel.einvoicegenerator.poc`) may stay German.
  - User-facing text is German where it belongs to the domain: invoice template content (German
    invoices), console dialog of the tool.
  - Review comments on pull requests may be German. Reply in the language of the comment you are
    answering.
- Conversations with the owner are in German.

## Conventions

- Package names follow `de.steffendingel.<projectname>`, here `de.steffendingel.einvoicegenerator`
  (the owner holds the domain `steffendingel.de`).
- Java 21, Maven.
- The repository is public: never commit real personal data (addresses, IBAN, tax IDs, customer
  data, real timesheets). Use obvious example data instead.

## Workflow

- Changes go through pull requests; `main` is protected. Claude never merges and never pushes to `main`
  (see `.claude/settings.json`).

## Build and run

- Build: `mvn compile`
- Proof of concept: `mvn compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.poc.PocMain`
  (optional `-Dexec.args="<output dir> <timesheet.pdf>"`), output in `target/poc/`.
