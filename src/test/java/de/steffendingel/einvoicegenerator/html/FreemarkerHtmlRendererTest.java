package de.steffendingel.einvoicegenerator.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.steffendingel.einvoicegenerator.model.Address;
import de.steffendingel.einvoicegenerator.model.Customer;
import de.steffendingel.einvoicegenerator.model.Invoice;
import de.steffendingel.einvoicegenerator.model.Seller;
import de.steffendingel.einvoicegenerator.model.TestData;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;

class FreemarkerHtmlRendererTest {

    private static final List<String> ATTACHMENTS = List.of("Stundennachweis 10/2026");

    @TempDir
    Path tempDir;

    private static String render(Invoice invoice) throws IOException {
        return new FreemarkerHtmlRenderer().renderHtml(invoice, ATTACHMENTS);
    }

    /** Parses the output as XML, as openhtmltopdf does; fails if it is not well-formed. */
    private static void assertWellFormedXhtml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.newDocumentBuilder().parse(new InputSource(new StringReader(xhtml)));
    }

    @Nested
    class BuiltInTemplate {

        @Test
        void containsTheInvoiceDataInGermanFormat() throws IOException {
            String html = render(TestData.invoice());

            assertThat(html)
                    .contains("Rechnung Nr. 302")
                    .contains("Rechnungsdatum: 02.11.2026")
                    .contains("01.10.26 – 31.10.26")
                    .contains("37,50")
                    .contains("95,00 €")
                    .contains("3.562,50 €")
                    .contains("Umsatzsteuer 19 %")
                    .contains("676,88 €")
                    .contains("4.239,38 €")
                    .contains("bis zum 02.12.26");
        }

        @Test
        void takesXmlRelevantSellerDataFromTheSettings() throws IOException {
            String html = render(TestData.invoice());

            assertThat(html)
                    .contains("Max Mustermann")
                    .contains("Musterstraße 1")
                    .contains("D-12345 Musterstadt")
                    .contains("USt-IdNr.: DE123456789")
                    .contains("IBAN: DE89 3704 0044 0532 0130 00")
                    .contains("BIC: COBADEFFXXX")
                    .contains("max@example.com");
        }

        @Test
        void showsTheTaxNumberIfThereIsNoVatId() throws IOException {
            Seller seller = new Seller("Max Mustermann", TestData.sellerAddress(), null, "123/456/78901",
                    "DE89370400440532013000", null, "max@example.com");
            Invoice example = TestData.invoice();
            Invoice invoice = new Invoice(example.number(), example.invoiceDate(), example.dueDate(),
                    example.period(), seller, example.customer(), example.hours(), example.hourlyRate(),
                    example.netAmount(), example.vatRate(), example.vatAmount(), example.grossAmount());

            String html = render(invoice);

            assertThat(html).contains("Steuernummer: 123/456/78901").doesNotContain("USt-IdNr.").doesNotContain("BIC:");
        }

        @Test
        void containsCustomerAndAttachments() throws IOException {
            String html = render(TestData.invoice());

            assertThat(html)
                    .contains("Beispiel GmbH")
                    .contains("Beispielweg 2")
                    .contains("54321 Beispielstadt")
                    .contains("Beratungs- und Entwicklungsleistungen")
                    .contains("Anlagen")
                    .contains("Stundennachweis 10/2026");
        }

        @Test
        void usesTheFreeFieldsIfPresent() throws IOException {
            Customer customer = TestData.customerWithFields(
                    Map.of("vertragsdatum", "15.01.2026", "ansprechpartner", "z. Hd. Erika Musterfrau"));

            String html = render(TestData.invoice(customer));

            assertThat(html)
                    .contains("gemäß unserem Vertrag vom 15.01.2026 erlaube ich mir")
                    .contains("z. Hd. Erika Musterfrau");
        }

        @Test
        void fallsBackIfFreeFieldsAreMissing() throws IOException {
            String html = render(TestData.invoice(TestData.customerWithFields(Map.of())));

            assertThat(html)
                    .contains("hiermit erlaube ich mir")
                    .doesNotContain("Vertrag vom")
                    .doesNotContain("z. Hd.");
        }

        @Test
        void omitsAttachmentsSectionWithoutAttachments() throws IOException {
            String html = new FreemarkerHtmlRenderer().renderHtml(TestData.invoice(), List.of());

            assertThat(html).doesNotContain("Anlagen");
        }

        @Test
        void escapesSpecialCharactersAndStaysWellFormed() throws Exception {
            Customer example = TestData.customer();
            Customer customer = new Customer(example.id(), "Müller & Söhne <GmbH>",
                    new Address("Weg \"A\" 1", "54321", "Ort", "DE"), null, null, example.hourlyRate(),
                    example.paymentTermDays(), "Beratung & Entwicklung", Map.of("ansprechpartner", "z. Hd. <Chef>"));

            String html = render(TestData.invoice(customer));

            assertThat(html)
                    .contains("Müller &amp; Söhne &lt;GmbH&gt;")
                    .contains("Beratung &amp; Entwicklung")
                    .contains("z. Hd. &lt;Chef&gt;")
                    .doesNotContain("<GmbH>");
            assertWellFormedXhtml(html);
        }

        @Test
        void producesWellFormedXhtml() throws Exception {
            assertWellFormedXhtml(render(TestData.invoice()));
        }
    }

    @Nested
    class Logo {

        @Test
        void isOmittedWithoutLogo() throws IOException {
            assertThat(render(TestData.invoice())).doesNotContain("<img");
        }

        @Test
        void isReferencedAsFileUri() throws IOException {
            Path logo = Files.writeString(tempDir.resolve("logo.svg"), "<svg/>");

            String html = new FreemarkerHtmlRenderer(null, logo).renderHtml(TestData.invoice(), ATTACHMENTS);

            assertThat(html).contains("<img src=\"" + logo.toUri() + "\"");
        }

        @Test
        void mustExist() {
            assertThatThrownBy(() -> new FreemarkerHtmlRenderer(null, tempDir.resolve("missing.svg")))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("logo not found");
        }
    }

    @Nested
    class OwnTemplate {

        @Test
        void isUsedInsteadOfTheBuiltInOne() throws IOException {
            Path template = Files.writeString(tempDir.resolve("own.ftlh"),
                    "<p>${invoice.number} ${customer.name} ${format.money(invoice.grossAmount)}"
                            + " ${customer.fields.kostenstelle!\"-\"}</p>");

            String html = new FreemarkerHtmlRenderer(template, null).renderHtml(TestData.invoice(), ATTACHMENTS);

            assertThat(html).isEqualTo("<p>302 Beispiel GmbH 4.239,38 € -</p>");
        }

        @Test
        void reportsErrorsWithTemplateNameAndPosition() throws IOException {
            Path template = Files.writeString(tempDir.resolve("broken.ftlh"), "<p>\n${customer.nam}</p>");

            assertThatThrownBy(() -> new FreemarkerHtmlRenderer(template, null).renderHtml(TestData.invoice(), ATTACHMENTS))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("broken.ftlh")
                    .hasMessageContaining("customer.nam")
                    .hasMessageContaining("line 2");
        }

        @Test
        void mustExist() {
            assertThatThrownBy(() -> new FreemarkerHtmlRenderer(tempDir.resolve("missing.ftlh"), null))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("template not found");
        }
    }
}
