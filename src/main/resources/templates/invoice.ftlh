<?xml version="1.0" encoding="UTF-8"?>
<#--
  Built-in invoice template (FreeMarker, output: XHTML for openhtmltopdf).

  To use your own data and layout: copy this file to your configuration directory, adjust it and set
  "template:" in settings.yaml.

  IMPORTANT: PDF and XML must not contradict each other. Everything that is also in the e-invoice XML
  must come from the variables below, never typed in: seller name, address, e-mail, VAT ID / tax number,
  IBAN, BIC; all customer and invoice data. Only details that are NOT in the XML may be written directly
  into the template: job title, phone, mobile, website, bank name (marked "display only" below).

  Variables:
    invoice      number, invoiceDate, dueDate, period.from/to, hours, hourlyRate, netAmount, vatRate,
                 vatAmount, grossAmount
    seller       name, address.street/zip/city/country, vatId, taxNumber, iban, bic, email
    customer     name, address.street/zip/city/country, vatId, email, itemText,
                 fields.<key> (free placeholders from customers.yaml, may be missing)
    attachments  list of attachment labels, e.g. "Stundennachweis 10/2026"
    logo         file URI of the logo (missing if no logo is configured)
    format       money(), decimal(), percent(), date(), shortDate(), iban()

  Missing optional values: ${x!} prints nothing, <#if x??>...</#if> tests for presence.
  Text is escaped automatically (.ftlh), e.g. "&" becomes "&amp;".
-->
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="de">
<head>
<meta charset="UTF-8"/>
<title>Rechnung Nr. ${invoice.number}</title>
<meta name="author" content="${seller.name}"/>
<meta name="subject" content="Rechnung Nr. ${invoice.number}"/>
<style>
  @page {
    size: A4;
    /* Left/right page margins are implemented as body padding: openhtmltopdf clips everything
       outside the page content area, but the letterhead sits in the top margin.
       Margins as in the Word template: left 2.5 cm, right 2 cm. */
    margin: 1cm 0 3.8cm 0;
    @bottom-center { content: element(footer); vertical-align: top; }
  }
  body { font-family: 'Carlito'; font-size: 12pt; line-height: 1.15; margin: 0;
         padding: 0 2cm 0 2.5cm; }
  p { margin: 0 0 10pt 0; }

  /* Letterhead top right: logo + sender */
  .letterhead { position: absolute; top: 0.45cm; right: 2cm; }
  .letterhead td { vertical-align: top; padding: 0; }
  .letterhead .logo { width: 1.9cm; height: 1.9cm; padding-right: 0.25cm; }
  .letterhead .logo img { width: 1.9cm; height: 1.9cm; }
  .letterhead .sender { font-size: 10.5pt; line-height: 1.3; }

  /* Address field */
  .address-block { position: absolute; top: 4.3cm; left: 2.5cm; width: 8.5cm; }
  .return-address { font-size: 7pt; text-decoration: underline; margin-bottom: 8pt; }
  .recipient { line-height: 1.4; }
  .recipient .city { margin-top: 6pt; }

  .content { padding-top: 8.9cm; }
  .invoice-date { text-align: right; margin-bottom: 14pt; }
  .title { font-weight: bold; }

  /* Line items table */
  table.items { width: 100%; border-collapse: collapse; margin: 14pt 0 12pt 0; }
  table.items th { font-weight: normal; font-style: italic; text-align: left;
                   border-top: 0.75pt solid #000000; border-bottom: 0.75pt solid #000000;
                   padding: 4pt 0 4pt 0; }
  table.items td { vertical-align: top; padding: 6pt 0 4pt 0; }
  table.items .num { text-align: right; }
  table.items .center { text-align: center; }
  table.items tr.item td { border-bottom: 0.75pt solid #000000; }
  table.items tr.total td { font-weight: bold; }
  table.items tr.total td.amount { border-top: 0.75pt solid #000000; }

  .attachments { margin-top: 2.6cm; }
  .attachments .label { font-weight: bold; }

  /* Footer on every page */
  .footer { position: running(footer); width: 16.5cm; margin-left: 2.5cm; font-size: 10pt; line-height: 1.35;
            border-top: 0.5pt solid #808080; padding-top: 6pt; }
  .footer table { width: 100%; border-collapse: collapse; }
  .footer td { vertical-align: top; padding: 0; }
  .footer .mark { vertical-align: bottom; text-align: right; width: 0.5cm; }
  .footer .mark div { width: 0.33cm; height: 0.33cm; background: #e2231a; margin-left: auto; }
</style>
</head>
<#-- Postal code with the "D-" prefix of the original layout for German addresses -->
<#function cityLine address><#return (address.country == "DE")?then("D-", "") + address.zip + " " + address.city></#function>
<body>

<div class="footer">
  <table>
    <tr>
      <td style="width: 5.2cm">
        ${seller.name}<br/>
        ${seller.address.street}<br/>
        ${cityLine(seller.address)}<br/>
        <#if seller.vatId??>USt-IdNr.: ${seller.vatId}<#else>Steuernummer: ${seller.taxNumber}</#if>
      </td>
      <td style="width: 5.5cm">
        <#-- display only (not in the XML): -->
        Tel. +49 000 0000000<br/>
        Mobil +49 000 1111111<br/>
        ${seller.email}<br/>
        www.example.com
      </td>
      <td>
        Bankverbindung:<br/>
        <#-- display only (not in the XML): -->
        Beispielbank Musterstadt<br/>
        IBAN: ${format.iban(seller.iban)}<br/>
        <#if seller.bic??>BIC: ${seller.bic}</#if>
      </td>
      <td class="mark"><div></div></td>
    </tr>
  </table>
</div>

<table class="letterhead">
  <tr>
    <#if logo??>
    <td class="logo"><img src="${logo}" alt="Logo"/></td>
    </#if>
    <td class="sender">
      ${seller.name}<br/>
      <#-- display only (not in the XML): -->
      Freiberuflicher Beispiel-Berater<br/>
      ${seller.address.street}<br/>
      ${cityLine(seller.address)}
    </td>
  </tr>
</table>

<div class="address-block">
  <div class="return-address">${seller.name}, ${seller.address.street}, ${cityLine(seller.address)}</div>
  <div class="recipient">
    ${customer.name}<br/>
    <#if customer.fields.ansprechpartner??>${customer.fields.ansprechpartner}<br/></#if>
    ${customer.address.street}
    <div class="city">${customer.address.zip} ${customer.address.city}</div>
  </div>
</div>

<div class="content">
  <div class="invoice-date">Rechnungsdatum: ${format.date(invoice.invoiceDate)}</div>

  <p class="title">Rechnung Nr. ${invoice.number}</p>

  <p>Sehr geehrte Damen und Herren,</p>

  <p><#if customer.fields.vertragsdatum??>gemäß unserem Vertrag vom ${customer.fields.vertragsdatum} erlaube<#else>hiermit erlaube</#if> ich mir, folgende Leistungen zu berechnen:</p>

  <table class="items">
    <thead>
      <tr>
        <th style="width: 5.0cm">Leistung</th>
        <th style="width: 3.75cm" class="center">Leistungszeitraum</th>
        <th style="width: 1.75cm" class="center">Einheit</th>
        <th style="width: 1.5cm" class="center">Menge</th>
        <th style="width: 2.25cm" class="num">Einzelpreis</th>
        <th style="width: 2.25cm" class="num">Gesamtpreis</th>
      </tr>
    </thead>
    <tbody>
      <tr class="item">
        <td>${customer.itemText}</td>
        <td class="center">${format.shortDate(invoice.period.from)} – ${format.shortDate(invoice.period.to)}</td>
        <td class="center">Stunden</td>
        <td class="center">${format.decimal(invoice.hours)}</td>
        <td class="num">${format.money(invoice.hourlyRate)}</td>
        <td class="num">${format.money(invoice.netAmount)}</td>
      </tr>
      <tr>
        <td colspan="5">Summe (netto)</td>
        <td class="num">${format.money(invoice.netAmount)}</td>
      </tr>
      <tr>
        <td colspan="5">Umsatzsteuer ${format.percent(invoice.vatRate)}</td>
        <td class="num">${format.money(invoice.vatAmount)}</td>
      </tr>
      <tr class="total">
        <td colspan="5">Rechnungsbetrag</td>
        <td class="num amount">${format.money(invoice.grossAmount)}</td>
      </tr>
    </tbody>
  </table>

  <p>Bitte überweisen Sie den Rechnungsbetrag bis zum ${format.shortDate(invoice.dueDate)} auf das unten angegebene Konto.<br/>
  Vielen Dank für Ihren Auftrag!</p>

  <p>Mit freundlichen Grüßen</p>

  <#if attachments?has_content>
  <div class="attachments">
    <div class="label">Anlagen</div>
    <#list attachments as attachment>
    <div>${attachment}</div>
    </#list>
  </div>
  </#if>
</div>

</body>
</html>
