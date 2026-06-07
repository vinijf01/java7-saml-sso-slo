package id.co.vini.poc.plugin.handler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import id.co.vini.poc.plugin.util.CookieUtil;
import id.co.vini.poc.plugin.util.SamlConfig;
import id.co.vini.poc.plugin.util.SamlMessageUtil;

/**
 * Handles SAML callbacks at /callbacksso.
 * Routes SAMLResponse (AuthnResponse or LogoutResponse) and SAMLRequest (LogoutRequest).
 * Uses slo_in_progress cookie to distinguish LogoutResponse from AuthnResponse.
 */
public class CallbackHandler {

    private SamlConfig config;
    private LoginHandler loginHandler;

    /**
     * Initializes the handler with config and login handler.
     */
    public CallbackHandler(SamlConfig config, LoginHandler loginHandler) {
        this.config = config;
        this.loginHandler = loginHandler;
    }

    /**
     * Handles the callback request.
     * Routes to appropriate handler based on SAML message type.
     */
    public void handle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String samlResponse = request.getParameter("SAMLResponse");
        String samlRequest = request.getParameter("SAMLRequest");

        if (samlResponse != null && !samlResponse.isEmpty()) {
            handleSamlResponse(request, response, samlResponse);
        } else if (samlRequest != null && !samlRequest.isEmpty()) {
            handleSamlRequest(request, response, samlRequest);
        } else {
            System.err.println("[CallbackHandler] No SAMLResponse or SAMLRequest parameter");
            response.sendRedirect("login.htm?error=invalid_callback");
        }
    }

    /**
     * Handles SAMLResponse from IdP.
     * Checks slo_in_progress cookie to determine if it's a LogoutResponse or AuthnResponse.
     */
    private void handleSamlResponse(HttpServletRequest request, HttpServletResponse response, String encodedResponse) throws Exception {
        boolean sloInProgress = CookieUtil.isSloInProgress(request);

        if (sloInProgress) {
            // This is a LogoutResponse from IdP
            System.out.println("[CallbackHandler] Received LogoutResponse (slo_in_progress=true)");
            CookieUtil.clearSloInProgress(response);
            response.sendRedirect("login.htm");
        } else {
            // This is an AuthnResponse from IdP
            System.out.println("[CallbackHandler] Received AuthnResponse");
            loginHandler.handleAuthnResponse(request, response);
        }
    }

    /**
     * Handles SAMLRequest from IdP (IdP-initiated logout).
     * Invalidates session, builds LogoutResponse, and redirects back to IdP.
     */
    private void handleSamlRequest(HttpServletRequest request, HttpServletResponse response, String encodedRequest) throws Exception {
        System.out.println("[CallbackHandler] Received SAMLRequest (IdP-initiated logout)");

        // Decode and extract ID from LogoutRequest
        String decodedXml = SamlMessageUtil.decodeSamlMessage(encodedRequest);
        String requestId = SamlMessageUtil.extractId(decodedXml);
        System.out.println("[CallbackHandler] LogoutRequest ID: " + requestId);

        // Invalidate local session
        HttpSession session = request.getSession(false);
        if (session != null) {
            System.out.println("[CallbackHandler] Invalidating session for: " + session.getAttribute("username"));
            session.invalidate();
        }

        // Build LogoutResponse
        String spEntityId = config.getProperty("sp.entity-id");
        String idpSloUrl = config.getIdpSloUrl();

        String logoutResponseXml = SamlMessageUtil.buildLogoutResponse(requestId, spEntityId, idpSloUrl);
        System.out.println("[CallbackHandler] LogoutResponse built");

        // Encode: deflate -> base64 -> URL encode
        String encodedResponse = SamlMessageUtil.encodeSamlMessage(logoutResponseXml);

        // Redirect back to IdP SLO URL
        String redirectUrl = idpSloUrl + "?SAMLResponse=" + encodedResponse + "&RelayState=logout";
        System.out.println("[CallbackHandler] Redirecting to IdP SLO URL");
        response.sendRedirect(redirectUrl);
    }
}
