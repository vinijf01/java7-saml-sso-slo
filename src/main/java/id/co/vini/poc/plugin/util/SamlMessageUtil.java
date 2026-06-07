package id.co.vini.poc.plugin.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.zip.Deflater;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Utility for building and encoding SAML messages manually.
 * Handles LogoutRequest, LogoutResponse XML construction,
 * deflate, base64 encoding, and URL encoding.
 */
public class SamlMessageUtil {

    private static final String SAML_NS = "urn:oasis:names:tc:SAML:2.0:assertion";
    private static final String SAML_PROTOCOL_NS = "urn:oasis:names:tc:SAML:2.0:protocol";
    private static final String SAML_BINDING_REDIRECT = "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect";

    /**
     * Builds a SAML 2.0 AuthnRequest XML string.
     */
    public static String buildAuthnRequest(String spEntityId, String acsUrl, String idpSsoUrl) throws Exception {
        String id = "ID_" + UUID.randomUUID().toString();
        String issueInstant = nowAsXmlDateTime();

        StringBuilder xml = new StringBuilder();
        xml.append("<samlp:AuthnRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ");
        xml.append("xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ");
        xml.append("ID=\"").append(id).append("\" ");
        xml.append("Version=\"2.0\" ");
        xml.append("IssueInstant=\"").append(issueInstant).append("\" ");
        xml.append("Destination=\"").append(idpSsoUrl).append("\" ");
        xml.append("ProtocolBinding=\"urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST\" ");
        xml.append("AssertionConsumerServiceURL=\"").append(acsUrl).append("\">");
        xml.append("<saml:Issuer>").append(escapeXml(spEntityId)).append("</saml:Issuer>");
        xml.append("<samlp:NameIDPolicy Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\" AllowCreate=\"true\"/>");
        xml.append("</samlp:AuthnRequest>");

        return xml.toString();
    }

    /**
     * Builds a SAML 2.0 LogoutRequest XML string.
     */
    public static String buildLogoutRequest(String nameId, String spEntityId, String idpSloUrl) throws Exception {
        String id = "ID_" + UUID.randomUUID().toString();
        String issueInstant = nowAsXmlDateTime();

        StringBuilder xml = new StringBuilder();
        xml.append("<samlp:LogoutRequest xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ");
        xml.append("xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ");
        xml.append("ID=\"").append(id).append("\" ");
        xml.append("Version=\"2.0\" ");
        xml.append("IssueInstant=\"").append(issueInstant).append("\" ");
        xml.append("Destination=\"").append(idpSloUrl).append("\">");
        xml.append("<saml:Issuer>").append(escapeXml(spEntityId)).append("</saml:Issuer>");
        xml.append("<saml:NameID Format=\"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\">");
        xml.append(escapeXml(nameId));
        xml.append("</saml:NameID>");
        xml.append("</samlp:LogoutRequest>");

        return xml.toString();
    }

    /**
     * Builds a SAML 2.0 LogoutResponse XML string.
     */
    public static String buildLogoutResponse(String inResponseTo, String spEntityId, String idpSloUrl) throws Exception {
        String id = "ID_" + UUID.randomUUID().toString();
        String issueInstant = nowAsXmlDateTime();

        StringBuilder xml = new StringBuilder();
        xml.append("<samlp:LogoutResponse xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" ");
        xml.append("xmlns:saml=\"urn:oasis:names:tc:SAML:2.0:assertion\" ");
        xml.append("ID=\"").append(id).append("\" ");
        xml.append("Version=\"2.0\" ");
        xml.append("IssueInstant=\"").append(issueInstant).append("\" ");
        xml.append("Destination=\"").append(idpSloUrl).append("\" ");
        xml.append("InResponseTo=\"").append(inResponseTo).append("\">");
        xml.append("<saml:Issuer>").append(escapeXml(spEntityId)).append("</saml:Issuer>");
        xml.append("<samlp:Status>");
        xml.append("<samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>");
        xml.append("</samlp:Status>");
        xml.append("</samlp:LogoutResponse>");

        return xml.toString();
    }

    /**
     * Deflates, base64-encodes, and URL-encodes a SAML message.
     */
    public static String encodeSamlMessage(String xml) throws Exception {
        byte[] xmlBytes = xml.getBytes("UTF-8");

        // Deflate
        Deflater deflater = new Deflater(Deflater.DEFLATED, true);
        deflater.setInput(xmlBytes);
        deflater.finish();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        byte[] deflated = baos.toByteArray();

        // Base64
        String base64 = Base64.encodeBase64String(deflated);

        // URL encode
        return URLEncoder.encode(base64, "UTF-8");
    }

    /**
     * Base64-encodes a SAML message for HTTP-POST binding (no deflation, no URL encoding).
     */
    public static String encodeSamlMessageForPost(String xml) throws Exception {
        byte[] xmlBytes = xml.getBytes("UTF-8");

        // Base64 only (no deflation for POST binding)
        return Base64.encodeBase64String(xmlBytes);
    }

    /**
     * Decodes a base64-encoded, deflated SAML message.
     */
    public static String decodeSamlMessage(String encoded) throws Exception {
        byte[] decoded = Base64.decodeBase64(encoded);

        java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
        inflater.setInput(decoded);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!inflater.finished()) {
            int count = inflater.inflate(buffer);
            baos.write(buffer, 0, count);
        }
        inflater.end();

        return baos.toString("UTF-8");
    }

    /**
     * Parses the IdP SLO URL from IdP metadata XML.
     */
    public static String parseIdpSloUrl(InputStream metadataXml) throws Exception {
        Document doc = parseXml(metadataXml);

        NodeList sloList = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:metadata", "SingleLogoutService");
        for (int i = 0; i < sloList.getLength(); i++) {
            Element el = (Element) sloList.item(i);
            String binding = el.getAttribute("Binding");
            if (SAML_BINDING_REDIRECT.equals(binding)) {
                return el.getAttribute("Location");
            }
        }

        // Fallback to first SingleLogoutService
        if (sloList.getLength() > 0) {
            Element el = (Element) sloList.item(0);
            return el.getAttribute("Location");
        }

        throw new RuntimeException("SingleLogoutService not found in IdP metadata");
    }

    /**
     * Extracts the ID attribute from a SAML message XML string.
     */
    public static String extractId(String xml) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = parseXml(bais);

        Element root = doc.getDocumentElement();
        String id = root.getAttribute("ID");
        if (id == null || id.isEmpty()) {
            throw new RuntimeException("ID attribute not found in SAML message");
        }
        return id;
    }

    /**
     * Extracts the InResponseTo attribute from a SAML message XML string.
     */
    public static String extractInResponseTo(String xml) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = parseXml(bais);

        Element root = doc.getDocumentElement();
        return root.getAttribute("InResponseTo");
    }

    /**
     * Decodes a base64-encoded SAML message from HTTP-POST binding (no inflation).
     */
    public static String decodePostBindingSamlMessage(String encoded) throws Exception {
        byte[] decoded = Base64.decodeBase64(encoded);
        return new String(decoded, "UTF-8");
    }

    /**
     * Extracts the NameID value from a SAML Response XML string.
     */
    public static String extractNameId(String xml) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = parseXml(bais);

        NodeList nameIdList = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "NameID");
        if (nameIdList.getLength() > 0) {
            Element nameIdEl = (Element) nameIdList.item(0);
            return nameIdEl.getTextContent();
        }
        throw new RuntimeException("NameID not found in SAML Response");
    }

    /**
     * Extracts an attribute value from a SAML Response XML string by attribute name.
     */
    public static String extractAttribute(String xml, String attributeName) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = parseXml(bais);

        NodeList attrList = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "Attribute");
        for (int i = 0; i < attrList.getLength(); i++) {
            Element attrEl = (Element) attrList.item(i);
            String name = attrEl.getAttribute("Name");
            if (attributeName.equals(name)) {
                NodeList valueList = attrEl.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:assertion", "AttributeValue");
                if (valueList.getLength() > 0) {
                    Element valueEl = (Element) valueList.item(0);
                    return valueEl.getTextContent();
                }
            }
        }
        return null;
    }

    /**
     * Parses XML with XXE hardening.
     */
    private static Document parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(is);
    }

    /**
     * Gets current time as SAML 2.0 xs:dateTime format.
     */
    private static String nowAsXmlDateTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    /**
     * Escapes special XML characters.
     */
    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
