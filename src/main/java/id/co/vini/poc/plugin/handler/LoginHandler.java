package id.co.vini.poc.plugin.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import id.co.vini.poc.plugin.util.SamlConfig;
import id.co.vini.poc.plugin.util.SamlMessageUtil;

/**
 * Handles SAML AuthnResponse processing.
 * Extracts NameID and attributes, performs group mapping via group.xml,
 * and sets session attributes.
 */
public class LoginHandler {

    private static final String DEFAULT_GROUP = "viewer";

    private SamlConfig config;
    private Map<String, String> groupMappings;
    private Map<String, String> userMappings;

    /**
     * Initializes the handler with config and group mappings.
     */
    public LoginHandler(SamlConfig config) throws Exception {
        this.config = config;
        this.groupMappings = new HashMap<String, String>();
        this.userMappings = new HashMap<String, String>();
        loadGroupConfig();
    }

    /**
     * Processes the SAML AuthnResponse and sets up the user session.
     */
    public void handleAuthnResponse(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[LoginHandler] Processing AuthnResponse...");

        String encodedResponse = request.getParameter("SAMLResponse");
        if (encodedResponse == null || encodedResponse.isEmpty()) {
            System.err.println("[LoginHandler] No SAMLResponse parameter found");
            response.sendRedirect("login.htm?error=auth_failed");
            return;
        }

        // Decode SAMLResponse (POST binding: base64 only, no deflate)
        String samlXml = SamlMessageUtil.decodePostBindingSamlMessage(encodedResponse);
        System.out.println("[LoginHandler] SAMLResponse decoded");

        // Extract NameID and attributes manually
        String nameId = SamlMessageUtil.extractNameId(samlXml);
        String divisi = SamlMessageUtil.extractAttribute(samlXml, "divisi");

        System.out.println("[LoginHandler] NameID: " + nameId);
        System.out.println("[LoginHandler] Divisi: " + divisi);

        String group = resolveGroup(nameId, divisi);
        System.out.println("[LoginHandler] Resolved group: " + group);

        HttpSession session = request.getSession(true);
        session.setAttribute("username", nameId);
        session.setAttribute("samlNameId", nameId);
        session.setAttribute("group", group);
        session.setAttribute("location", divisi != null ? divisi : "");

        System.out.println("[LoginHandler] Session created for: " + nameId);

        response.sendRedirect("index.htm");
    }

    /**
     * Resolves the application group for a user.
     * User mapping takes priority over group mapping.
     */
    private String resolveGroup(String username, String divisi) {
        // Check user mapping first (higher priority)
        String userGroup = userMappings.get(username);
        if (userGroup != null) {
            System.out.println("[LoginHandler] Using user mapping for: " + username);
            return userGroup;
        }

        // Check group mapping
        if (divisi != null && !divisi.isEmpty()) {
            String groupMapping = groupMappings.get(divisi);
            if (groupMapping != null) {
                System.out.println("[LoginHandler] Using group mapping for divisi: " + divisi);
                return groupMapping;
            }
        }

        // Default group
        System.out.println("[LoginHandler] Using default group: " + DEFAULT_GROUP);
        return DEFAULT_GROUP;
    }

    /**
     * Loads group and user mappings from group.xml.
     */
    private void loadGroupConfig() throws Exception {
        String groupPath = config.getProperty("group.config.path", "group.xml");
        System.out.println("[LoginHandler] Loading GROUP config from: " + groupPath);

        InputStream is = getClass().getClassLoader().getResourceAsStream(groupPath);
        if (is == null) {
            File f = new File(groupPath);
            if (f.exists()) {
                is = new FileInputStream(f);
            }
        }

        if (is == null) {
            System.err.println("[LoginHandler] GROUP config not found, using defaults");
            return;
        }

        try {
            Document doc = parseXml(is);

            // Load group mappings
            NodeList groupMappingsList = doc.getElementsByTagName("mapping");
            for (int i = 0; i < groupMappingsList.getLength(); i++) {
                Element el = (Element) groupMappingsList.item(i);
                Element parent = (Element) el.getParentNode();
                String tagName = parent.getTagName();

                if ("groupmapping".equals(tagName)) {
                    String divisi = el.getAttribute("divisi");
                    String group = el.getAttribute("group");
                    if (divisi != null && !divisi.isEmpty() && group != null && !group.isEmpty()) {
                        groupMappings.put(divisi, group);
                        System.out.println("[LoginHandler] Group mapping: " + divisi + " -> " + group);
                    }
                } else if ("usermapping".equals(tagName)) {
                    String username = el.getAttribute("username");
                    String group = el.getAttribute("group");
                    if (username != null && !username.isEmpty() && group != null && !group.isEmpty()) {
                        userMappings.put(username, group);
                        System.out.println("[LoginHandler] User mapping: " + username + " -> " + group);
                    }
                }
            }
        } finally {
            is.close();
        }
    }

    /**
     * Parses XML with XXE hardening.
     */
    private Document parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(is);
    }
}
