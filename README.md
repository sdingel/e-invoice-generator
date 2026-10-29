# E-Invoice Generator

Command-line tool for freelancers that creates monthly invoices as hybrid **ZUGFeRD e-invoices**
(PDF/A-3 with embedded XML, profile EN16931), including a timesheet.

> **Status:** proof of concept done, implementation not started yet. Plan and decisions (German):
> [`PLAN.md`](PLAN.md).

## How an invoice is generated

The diagram shows the data flow as implemented in the proof of concept
(`de.steffendingel.einvoicegenerator.poc.PocMain`).

Legend: blue parallelograms are inputs, grey parallelograms are intermediate results and output,
rectangles are processing steps, hexagons are checks that abort on failure. Dashed shapes are planned
but not part of the proof of concept yet.

```mermaid
flowchart TD
    %% Inputs
    data[/"Invoice data<br/>customer, hours, period, number"/]
    template[/"FreeMarker template<br/>invoice.ftlh"/]
    fonts[/"Fonts (Carlito TTF)<br/>sRGB color profile"/]
    timesheet[/"Timesheet PDF<br/>PDF/A or plain PDF"/]

    %% Visible invoice
    freemarker["<b>FreeMarker</b><br/>fill template"]
    xhtml[/"XHTML<br/>(also usable as HTML preview)"/]
    openhtmltopdf["<b>openhtmltopdf</b><br/>render to PDF/A-3b"]
    invoicePdf[/"Invoice PDF<br/>PDF/A-3b, 1 page"/]
    textCheck{{"Read text back:<br/>number and total present?"}}

    data --> freemarker
    template --> freemarker
    freemarker --> xhtml --> openhtmltopdf
    fonts --> openhtmltopdf
    openhtmltopdf --> invoicePdf --> textCheck

    %% Timesheet pages
    append["<b>PDFBox</b><br/>append timesheet pages"]
    mergedPdf[/"Invoice + timesheet<br/>PDF/A-3b"/]
    mergedCheck{{"<b>veraPDF</b><br/>still PDF/A-3b?"}}

    textCheck --> append
    timesheet --> append
    append --> mergedPdf --> mergedCheck

    %% E-invoice
    mapper["Map invoice data<br/>to Mustang invoice"]
    cii[/"CII XML (EN16931)<br/>timesheet embedded as BG-24"/]
    mustang["<b>Mustang</b><br/>embed XML, write PDF/A-3b metadata"]
    hybrid[/"Hybrid e-invoice<br/>PDF/A-3b + factur-x.xml"/]

    data --> mapper
    timesheet -. "attached as BG-24" .-> mapper
    mapper --> cii --> mustang
    mergedCheck --> mustang
    mustang --> hybrid

    %% Final checks and output
    finalCheck{{"<b>veraPDF</b> + <b>Mustang validator</b><br/>PDF/A-3b, XML schema, Schematron"}}
    output[/"302_customer_2026-10.pdf"/]
    counter["Increment invoice number"]

    hybrid --> finalCheck --> output --> counter

    %% Styles
    classDef input fill:#e8f1fb,stroke:#4a7ab5,color:#000
    classDef artifact fill:#f4f4f4,stroke:#888,color:#000
    classDef check fill:#fff4e0,stroke:#c98a1b,color:#000
    classDef planned stroke-dasharray: 5 5
    class data,template,fonts,timesheet input
    class xhtml,invoicePdf,mergedPdf,cii,hybrid,output artifact
    class textCheck,mergedCheck,finalCheck check
    class textCheck,counter planned
```

| Step | Interface | Library | Result |
|---|---|---|---|
| Fill the template with the invoice data | `InvoiceDocumentRenderer` | FreeMarker 2.3.35 | XHTML (amounts and dates already formatted in Java) |
| Render XHTML to PDF | `InvoiceDocumentRenderer` | openhtmltopdf 1.1.100 (PDFBox 3) | Invoice page as PDF/A-3b, fonts embedded |
| Append the timesheet pages | `EInvoiceWriter` | PDFBox 3.0.8 | One PDF; output intent and metadata of the invoice apply to all pages |
| Check the merged PDF | `PdfAValidator` (used by `EInvoiceWriter`) | veraPDF (via Mustang validator) | Must still be PDF/A-3b, otherwise abort |
| Create and embed the XML | `EInvoiceWriter` | Mustang 2.26.0 | CII XML (EN16931) with the timesheet as BG-24, PDF/A-3b metadata |
| Validate the result | `EInvoiceValidator` | veraPDF + Mustang validator | PDF/A-3b, XML schema and Schematron rules |

Storing the file and incrementing the invoice number are done by `InvoiceArchive` and
`InvoiceNumberSource`; `InvoiceService` runs the steps. Signatures: [`PLAN.md`](PLAN.md), section 8.

Notes:

- The timesheet does **not** have to be PDF/A itself; what counts is the merged PDF.
- The timesheet appears twice: as visible pages and as an attachment in the XML (BG-24), so it also
  reaches recipients that only process the XML.
- The text read-back check exists because openhtmltopdf silently drops all text in PDF/A mode when
  no font is available, and veraPDF still reports the empty page as compliant.

## Build and run

Requirements: Java 21, Maven 3.9.

```sh
mvn compile

# proof of concept, output in target/poc/
mvn compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.poc.PocMain

# with your own timesheet
mvn compile exec:java -Dexec.mainClass=de.steffendingel.einvoicegenerator.poc.PocMain \
    -Dexec.args="target/poc /path/to/timesheet.pdf"
```

## Fonts

The template uses [Carlito](https://github.com/googlefonts/carlito), which is metric-compatible with
Calibri and licensed under the SIL Open Font License (see
[`src/main/resources/fonts/OFL-Carlito.txt`](src/main/resources/fonts/OFL-Carlito.txt)).
