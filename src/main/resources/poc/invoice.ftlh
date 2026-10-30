<?xml version="1.0" encoding="UTF-8"?>
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
  .logo-placeholder { width: 1.9cm; height: 1.2cm; padding-top: 0.7cm; background: #b4b4b4;
                      color: #ffffff; font-size: 8pt; text-align: center; }
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
<body>

<div class="footer">
  <table>
    <tr>
      <td style="width: 5.2cm">
        ${seller.name}<br/>
        ${seller.street}<br/>
        ${seller.countryPrefix}${seller.zip} ${seller.city}<br/>
        <#if seller.vatId??>USt-IdNr.: ${seller.vatId}<#else>Steuernummer: ${seller.taxNumber}</#if>
      </td>
      <td style="width: 5.5cm">
        <#if seller.phone??>Tel. ${seller.phone}<br/></#if>
        <#if seller.mobile??>Mobil ${seller.mobile}<br/></#if>
        ${seller.email}<br/>
        <#if seller.web??>${seller.web}</#if>
      </td>
      <td>
        Bankverbindung:<br/>
        ${seller.bankName}<br/>
        IBAN: ${seller.iban}<br/>
        BIC: ${seller.bic}
      </td>
      <td class="mark"><div></div></td>
    </tr>
  </table>
</div>

<table class="letterhead">
  <tr>
    <td class="logo">
      <#if logoUri??>
        <img src="${logoUri}" alt="Logo"/>
      <#else>
        <div class="logo-placeholder">LOGO</div>
      </#if>
    </td>
    <td class="sender">
      ${seller.name}<br/>
      <#if seller.tagline??>${seller.tagline}<br/></#if>
      ${seller.street}<br/>
      ${seller.countryPrefix}${seller.zip} ${seller.city}
    </td>
  </tr>
</table>

<div class="address-block">
  <div class="return-address">${seller.name}, ${seller.street}, ${seller.countryPrefix}${seller.zip} ${seller.city}</div>
  <div class="recipient">
    ${customer.name}<br/>
    <#if customer.contact??>${customer.contact}<br/></#if>
    ${customer.street}
    <div class="city">${customer.zip} ${customer.city}</div>
  </div>
</div>

<div class="content">
  <div class="invoice-date">Rechnungsdatum: ${invoice.date}</div>

  <p class="title">Rechnung Nr. ${invoice.number}</p>

  <p>Sehr geehrte Damen und Herren,</p>

  <p><#if invoice.contractDate??>gemäß unserem Vertrag vom ${invoice.contractDate} erlaube<#else>hiermit erlaube</#if> ich mir, folgende Leistungen zu berechnen:</p>

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
        <td>${invoice.itemText}</td>
        <td class="center">${invoice.periodFrom} – ${invoice.periodTo}</td>
        <td class="center">${invoice.unit}</td>
        <td class="center">${invoice.quantity}</td>
        <td class="num">${invoice.unitPrice}</td>
        <td class="num">${invoice.lineTotal}</td>
      </tr>
      <tr>
        <td colspan="5">Summe (netto)</td>
        <td class="num">${invoice.netTotal}</td>
      </tr>
      <tr>
        <td colspan="5">Umsatzsteuer ${invoice.vatRate}</td>
        <td class="num">${invoice.vatTotal}</td>
      </tr>
      <tr class="total">
        <td colspan="5">Rechnungsbetrag</td>
        <td class="num amount">${invoice.grossTotal}</td>
      </tr>
    </tbody>
  </table>

  <p>Bitte überweisen Sie den Rechnungsbetrag bis zum ${invoice.dueDate} auf das unten angegebene Konto.<br/>
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
