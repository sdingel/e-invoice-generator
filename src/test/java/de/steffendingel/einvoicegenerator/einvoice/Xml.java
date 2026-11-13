package de.steffendingel.einvoicegenerator.einvoice;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

/**
 * Reads values from the CII XML with XPath. Paths use local element names separated by "/", e.g.
 * {@code "ExchangedDocument/ID"}, so the tests stay readable without namespace prefixes.
 */
final class Xml {

    private final Document document;

    Xml(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        this.document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    /** Text of the first element at the path below the root, or "" if there is none. */
    String text(String path) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(toXPath(path), document).trim();
    }

    /** Value of an attribute of the first element at the path. */
    String attribute(String path, String attribute) throws Exception {
        return XPathFactory.newInstance().newXPath().evaluate(toXPath(path) + "/@" + attribute, document).trim();
    }

    int count(String path) throws Exception {
        return ((Number) XPathFactory.newInstance().newXPath()
                .evaluate("count(" + toXPath(path) + ")", document, XPathConstants.NUMBER)).intValue();
    }

    private static String toXPath(String path) {
        StringBuilder xpath = new StringBuilder("/*");
        for (String step : path.split("/")) {
            xpath.append("//*[local-name()='").append(step).append("']");
        }
        return "(" + xpath + ")[1]";
    }
}
