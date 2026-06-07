package id.co.vini.poc.plugin;

import java.io.IOException;
import java.net.URLEncoder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.pac4j.saml.client.SAML2Client;

import id.co.vini.poc.plugin.handler.CallbackHandler;
import id.co.vini.poc.plugin.handler.LoginHandler;
import id.co.vini.poc.plugin.handler.LogoutHandler;
import id.co.vini.poc.plugin.util.SamlConfig;
import id.co.vini.poc.plugin.util.SamlMessageUtil;

/**
 * Main servlet entry point for SAML SSO/ SLO operations.
 * Routes requests to appropriate handlers based on URL path.
 */
public class SSOServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private SamlConfig config;
    private SAML2Client client;
    private LoginHandler loginHandler;
    private LogoutHandler logoutHandler;
    private CallbackHandler callbackHandler;

    /**
     * Initializes SAML configuration and handlers.
     */
    @Override
    public void init() throws ServletException {
        try {
            System.out.println("[SSOServlet] Initializing SAML SSO Servlet...");
            config = new SamlConfig();
            client = config.getClient();

            loginHandler = new LoginHandler(config);
            logoutHandler = new LogoutHandler(config);
            callbackHandler = new CallbackHandler(config, loginHandler);

            System.out.println("[SSOServlet] Initialization complete");
        } catch (Exception e) {
            System.err.println("[SSOServlet] Initialization failed: " + e.getMessage());
            throw new ServletException("Failed to initialize SAML SSO", e);
        }
    }

    /**
     * Handles GET requests by routing to appropriate handler.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getRequestURI();
        System.out.println("[SSOServlet] GET request: " + path);

        try {
            if (path.endsWith("/loginsso")) {
                handleLogin(request, response);
            } else if (path.endsWith("/logoutsso")) {
                handleLogout(request, response);
            } else if (path.endsWith("/callbacksso")) {
                handleCallback(request, response);
            } else if (path.endsWith("/index.htm")) {
                showIndex(request, response);
            } else if (path.endsWith("/login.htm")) {
                showLogin(request, response);
            } else {
                response.sendRedirect("login.htm");
            }
        } catch (Exception e) {
            System.err.println("[SSOServlet] Error handling GET: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("login.htm?error=server_error");
        }
    }

    /**
     * Handles POST requests (SAMLResponse from IdP).
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getRequestURI();
        System.out.println("[SSOServlet] POST request: " + path);

        try {
            if (path.endsWith("/callbacksso")) {
                handleCallback(request, response);
            } else {
                doGet(request, response);
            }
        } catch (Exception e) {
            System.err.println("[SSOServlet] Error handling POST: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("login.htm?error=server_error");
        }
    }

    /**
     * Initiates SSO by building AuthnRequest and POSTing to IdP via auto-submit form.
     */
    private void handleLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[SSOServlet] Initiating SSO POST to IdP");

        String spEntityId = config.getProperty("sp.entity-id");
        String callbackUrl = config.getProperty("sp.callback-url");
        String idpSsoUrl = config.getIdpSsoUrl();

        // Build AuthnRequest manually
        String authnRequestXml = SamlMessageUtil.buildAuthnRequest(spEntityId, callbackUrl, idpSsoUrl);
        System.out.println("[SSOServlet] AuthnRequest built");

        // Encode: deflate -> base64 (no URL encoding for POST binding)
        String encodedRequest = SamlMessageUtil.encodeSamlMessageForPost(authnRequestXml);

        // Build auto-submit HTML form
        String relayState = request.getContextPath() + "/index.htm";
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().println("<html><body onload=\"document.forms[0].submit()\">");
        response.getWriter().println("<form method=\"post\" action=\"" + idpSsoUrl + "\">");
        response.getWriter().println("<input type=\"hidden\" name=\"SAMLRequest\" value=\"" + encodedRequest + "\"/>");
        response.getWriter().println("<input type=\"hidden\" name=\"RelayState\" value=\"" + relayState + "\"/>");
        response.getWriter().println("</form></body></html>");
        response.getWriter().flush();

        System.out.println("[SSOServlet] Auto-submit form sent to IdP SSO URL");
    }

    /**
     * Initiates SP-initiated SLO.
     */
    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[SSOServlet] Handling logout request");
        logoutHandler.handle(request, response);
    }

    /**
     * Handles SAML callback from IdP.
     */
    private void handleCallback(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("[SSOServlet] Handling SAML callback");
        callbackHandler.handle(request, response);
    }

    /**
     * Shows the index page if user is authenticated.
     */
    private void showIndex(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("username") != null) {
            request.getRequestDispatcher("/index.jsp").forward(request, response);
        } else {
            response.sendRedirect("login.htm");
        }
    }

    /**
     * Shows the login page.
     */
    private void showLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect("index.jsp");
    }
}
