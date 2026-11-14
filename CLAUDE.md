# e-invoice-generator

Command-line tool that generates hybrid ZUGFeRD e-invoices (PDF/A-3 with embedded XML) including a
timesheet. Planning happens in GitHub issues (see Workflow). `PLAN.md` holds the earlier plan and
decisions; it is being moved into issues and the README and will then be deleted.

## Language

- **Everything in the repository is English:** code, identifiers, comments, Javadoc, documentation,
  commit messages, pull requests and issues.
- **Exceptions:**
  - `PLAN.md` stays German.
  - User-facing text is German where it belongs to the German invoice: invoice template content, the
    default file name template and the timesheet label. The console dialog of the tool is English.
  - Review comments on pull requests may be German. Reply in the language of the comment you are
    answering.
- Conversations with the owner are in German.

## Conventions

- Package names follow `de.steffendingel.<projectname>`, here `de.steffendingel.einvoicegenerator`
  (the owner holds the domain `steffendingel.de`).
- Java 17 (the minimum version; build and CI use JDK 17), Maven.
- The repository is public: never commit real personal data (addresses, IBAN, tax IDs, customer
  data, real timesheets). Use obvious example data instead.

## Workflow with GitHub issues

- Planning and implementation are tracked in GitHub issues: a feature is discussed first, then one or
  more issues are written together with the user.
- Issues are implemented one at a time, and only when the user explicitly asks for it.
- Before starting to implement an issue, think it through and ask all foreseeable questions first, so
  that as few questions as possible come up while coding.
- One branch and one pull request per issue. Every change goes through a pull request; `main` is
  protected, never push to `main` (see `.claude/settings.json`).
- Only the user merges pull requests; never merge yourself.
- Pull requests are merged with a merge commit, not squashed, so the full history is kept. The history
  is meant to reflect the development process as it actually happened; it does not have to be clean,
  and correction commits are fine.
- Commit and push all work at the end of every turn, including work in progress, so that nothing exists
  only in the session. This happens on the branch of the issue; a turn without changes needs no commit.
  Within a turn, group the changes into meaningful commits before pushing.
- Never force-push. Pushed commits stay as they are; corrections are made with new commits.
- Every commit references its issue number in the subject line: `[#12] Short description`. Do not
  start the subject with `#`, because Git treats such lines as comments when a message is edited in an
  editor.
- Commits without an issue are allowed only in special cases the user has explicitly approved. They also
  go through a branch and a pull request, and their subject names the case instead of an issue number,
  e.g. `[CLAUDE.md] Short description`. Approved special cases:
  - Changes to this file (`CLAUDE.md`).
- When an issue is completely implemented and all open questions to the user are resolved, create the
  pull request without being asked.
- The pull request closes its issue automatically: put `Closes #12` in the description. If the pull
  request implements only part of the issue (which should be an exception), use `Refs #12` instead so
  the issue stays open.
- GitHub deletes a branch when its pull request is merged. Afterwards, update `main` with
  `git pull --prune` and delete the local copy of the branch.

## Build and run

- Build and test: `mvn verify` (runs the tests and the enforcer rules, builds `target/e-invoice-generator.jar`
  with the start scripts from `src/dist/`, then runs the `*IT` tests against the JAR); unit tests only: `mvn test`
- Application (dialog): `target/e-invoice-generator` after `mvn verify`, or
  `mvn -q compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.App`
  (optional `-Dexec.args="--config <directory>"`, default `~/rechnungen/config`)
