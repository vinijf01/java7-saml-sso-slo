package id.co.vini.poc.plugin.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import id.co.vini.poc.plugin.util.CookieUtil;
import id.co.vini.poc.plugin.util.SamlConfig;
import id.co.vini.poc.plugin.util.SamlMessageUtil;

/**
 * Handles SP-initiated Single Logout.
 * Builds LogoutRequest XML manually, encodes it, and redirects to IdP SLO URL.
 */
public class LogoutHandler {

    private SamlConfig config;

    /**
     * Initializes the handler with SAML config.
     */
    public LogoutHandler(SamlConfig config) {
        this.config = config;
    }

    /**
     * Handles the logout request.
     * If user has SSO session, initiates SLO flow with IdP.
     * Otherwise, performs local logout only.
     */
    public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpSession session = request.getSession(false);

        // No session or no SSO - just local logout
        if (session == null || session.getAttribute("samlNameId") == null) {
            System.out.println("[LogoutHandler] No SSO session, performing local logout");
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect("login.htm");
            return;
        }

        // SSO session exists - initiate SLO
        String nameId = (String) session.getAttribute("samlNameId");
        System.out.println("[LogoutHandler] Initiating SLO for NameID: " + nameId);

        // Invalidate local session
        session.invalidate();

        // Set slo_in_progress cookie
        int cookieMaxAge = Integer.parseInt(config.getProperty("session.cookie.max-age", "300"));
        CookieUtil.setSloInProgress(response, cookieMaxAge);

        // Build LogoutRequest
        String spEntityId = config.getProperty("sp.entity-id");
        String idpSloUrl = config.getIdpSloUrl();

        String logoutRequestXml = SamlMessageUtil.buildLogoutRequest(nameId, spEntityId, idpSloUrl);
        System.out.println("[LogoutHandler] LogoutRequest built");

        // Encode: deflate -> base64 -> URL encode
        String encodedRequest = SamlMessageUtil.encodeSamlMessage(logoutRequestXml);

        // Build redirect URL with SAMLRequest and RelayState
        String relayState = "logout";
        String redirectUrl = idpSloUrl + "?SAMLRequest=" + encodedRequest + "&RelayState=" + relayState;

        System.out.println("[LogoutHandler] Redirecting to IdP SLO URL");
        response.sendRedirect(redirectUrl);
    }
}
