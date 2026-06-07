package id.co.vini.poc.plugin.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Loads SAML configuration from properties file and builds SAML2Client.
 * Parses IdP metadata to extract SSO and SLO endpoints.
 */
public class SamlConfig {

    private static final String CONFIG_PATH_PROPERTY = "config.path";
    private static final String DEFAULT_CONFIG_RESOURCE = "saml.properties";

    private Properties props;
    private String idpSsoUrl;
    private String idpSloUrl;
    private String idpEntityId;
    private SAML2Client client;

    /**
     * Loads configuration and initializes SAML2Client.
     */
    public SamlConfig() throws Exception {
        props = loadProperties();
        parseIdpMetadata();
        buildClient();
    }

    /**
     * Loads properties from -Dconfig.path or fallback to classpath resource.
     */
    private Properties loadProperties() throws Exception {
        Properties p = new Properties();
        String configPath = System.getProperty(CONFIG_PATH_PROPERTY);

        if (configPath != null && !configPath.isEmpty()) {
            File f = new File(configPath);
            if (f.exists()) {
                System.out.println("[SamlConfig] Loading config from: " + configPath);
                FileInputStream fis = new FileInputStream(f);
                try {
                    p.load(fis);
                } finally {
                    fis.close();
                }
                return p;
            } else {
                System.err.println("[SamlConfig] Config file not found: " + configPath);
            }
        }

        System.out.println("[SamlConfig] Loading config from classpath: " + DEFAULT_CONFIG_RESOURCE);
        InputStream is = getClass().getClassLoader().getResourceAsStream(DEFAULT_CONFIG_RESOURCE);
        if (is == null) {
            throw new RuntimeException("SAML config not found: " + DEFAULT_CONFIG_RESOURCE);
        }
        try {
            p.load(is);
        } finally {
            is.close();
        }
        return p;
    }

    /**
     * Parses IdP metadata XML to extract SSO URL, SLO URL, and entity ID.
     */
    private void parseIdpMetadata() throws Exception {
        String metadataPath = props.getProperty("idp.metadata.path");
        if (metadataPath == null || metadataPath.isEmpty()) {
            throw new RuntimeException("idp.metadata.path not configured");
        }

        System.out.println("[SamlConfig] Parsing IdP metadata from: " + metadataPath);

        InputStream is = getClass().getClassLoader().getResourceAsStream(metadataPath);
        if (is == null) {
            File f = new File(metadataPath);
            if (f.exists()) {
                is = new FileInputStream(f);
            } else {
                throw new RuntimeException("IdP metadata not found: " + metadataPath);
            }
        }

        try {
            Document doc = parseXml(is);
            idpEntityId = getAttribute(doc, "EntityDescriptor", "entityID");

            NodeList ssoList = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:metadata", "SingleSignOnService");
            if (ssoList.getLength() > 0) {
                Element sso = (Element) ssoList.item(0);
                idpSsoUrl = sso.getAttribute("Location");
            }

            NodeList sloList = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:metadata", "SingleLogoutService");
            if (sloList.getLength() > 0) {
                Element slo = (Element) sloList.item(0);
                idpSloUrl = slo.getAttribute("Location");
            }

            if (idpSsoUrl == null || idpSsoUrl.isEmpty()) {
                throw new RuntimeException("IdP SSO URL not found in metadata");
            }
            if (idpSloUrl == null || idpSloUrl.isEmpty()) {
                throw new RuntimeException("IdP SLO URL not found in metadata");
            }

            System.out.println("[SamlConfig] IdP Entity ID: " + idpEntityId);
            System.out.println("[SamlConfig] IdP SSO URL: " + idpSsoUrl);
            System.out.println("[SamlConfig] IdP SLO URL: " + idpSloUrl);
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

    /**
     * Gets attribute value from first element with given local name.
     */
    private String getAttribute(Document doc, String localName, String attrName) {
        NodeList list = doc.getElementsByTagNameNS("urn:oasis:names:tc:SAML:2.0:metadata", localName);
        if (list.getLength() == 0) {
            list = doc.getElementsByTagName(localName);
        }
        if (list.getLength() > 0) {
            Element el = (Element) list.item(0);
            return el.getAttribute(attrName);
        }
        return null;
    }

    /**
     * Builds SAML2Client from loaded configuration.
     */
    private void buildClient() {
        String keystorePath = props.getProperty("sp.keystore.path");
        String keystorePassword = props.getProperty("sp.keystore.password");
        String privateKeyPassword = props.getProperty("sp.keystore.private-key-password");
        String defaultAlias = props.getProperty("sp.keystore.default-alias");
        String entityID = props.getProperty("sp.entity-id");
        String callbackUrl = props.getProperty("sp.callback-url");
        String idpMetadataPath = props.getProperty("idp.metadata.path");

        System.out.println("[SamlConfig] Building SAML2Client...");
        System.out.println("[SamlConfig] SP Entity ID: " + entityID);
        System.out.println("[SamlConfig] Callback URL: " + callbackUrl);

        SAML2ClientConfiguration cfg = new SAML2ClientConfiguration();
        cfg.setKeystorePath(keystorePath);
        cfg.setKeystorePassword(keystorePassword);
        cfg.setPrivateKeyPassword(privateKeyPassword);
        cfg.setKeystoreAlias(defaultAlias);
        cfg.setIdentityProviderMetadataPath(idpMetadataPath);
        cfg.setServiceProviderEntityId(entityID);

        client = new SAML2Client(cfg);
        client.setCallbackUrl(callbackUrl);
        client.setName("SAML2Client");

        System.out.println("[SamlConfig] SAML2Client initialized successfully");
    }

    /**
     * Gets the configured SAML2Client.
     */
    public SAML2Client getClient() {
        return client;
    }

    /**
     * Gets IdP SSO URL parsed from metadata.
     */
    public String getIdpSsoUrl() {
        return idpSsoUrl;
    }

    /**
     * Gets IdP SLO URL parsed from metadata.
     */
    public String getIdpSloUrl() {
        return idpSloUrl;
    }

    /**
     * Gets IdP entity ID parsed from metadata.
     */
    public String getIdpEntityId() {
        return idpEntityId;
    }

    /**
     * Gets a property value by key.
     */
    public String getProperty(String key) {
        return props.getProperty(key);
    }

    /**
     * Gets a property value by key with default fallback.
     */
    public String getProperty(String key, String defaultValue) {
        String val = props.getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        return val;
    }
}
