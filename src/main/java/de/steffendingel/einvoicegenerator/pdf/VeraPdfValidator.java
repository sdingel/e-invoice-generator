package de.steffendingel.einvoicegenerator.pdf;

import de.steffendingel.einvoicegenerator.model.ValidationResult;

import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Step ⑥ with veraPDF. Always checks against PDF/A-3b, the level the invoice must have, instead of the
 * level the file declares (a file without PDF/A identification would otherwise not be checkable).
 */
public final class VeraPdfValidator implements PdfAValidator {

    private static final PDFAFlavour FLAVOUR = PDFAFlavour.PDFA_3_B;

    /** More problems are not helpful for the user; the first ones show the cause. */
    private static final int MAX_PROBLEMS = 20;

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    @Override
    public ValidationResult validate(byte[] pdf) throws IOException {
        Objects.requireNonNull(pdf, "pdf must not be null");
        try (PDFAParser parser = Foundries.defaultInstance().createParser(new ByteArrayInputStream(pdf), FLAVOUR);
             PDFAValidator validator = Foundries.defaultInstance().createValidator(FLAVOUR, false)) {
            org.verapdf.pdfa.results.ValidationResult result = validator.validate(parser);
            if (result.isCompliant()) {
                return ValidationResult.valid("PDF/A-3b compliant");
            }
            List<String> problems = result.getTestAssertions().stream()
                    .filter(assertion -> assertion.getStatus() == TestAssertion.Status.FAILED)
                    .map(assertion -> "rule " + assertion.getRuleId().getClause() + "-"
                            + assertion.getRuleId().getTestNumber() + ": " + assertion.getMessage())
                    .distinct()
                    .limit(MAX_PROBLEMS)
                    .toList();
            return ValidationResult.invalid("not PDF/A-3b compliant", problems);
        } catch (EncryptedPdfException e) {
            throw new IOException("PDF is encrypted, cannot check PDF/A", e);
        } catch (ModelParsingException e) {
            throw new IOException("PDF cannot be read: " + e.getMessage(), e);
        } catch (ValidationException e) {
            throw new IOException("PDF/A check failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            // close() of parser and validator declares Exception
            throw new IOException("PDF/A check failed: " + e.getMessage(), e);
        }
    }
}
