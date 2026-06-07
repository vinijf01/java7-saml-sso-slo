package id.co.vini.poc.plugin.util;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utility for managing the slo_in_progress cookie.
 * This cookie distinguishes LogoutResponse from AuthnResponse at /callbacksso.
 */
public class CookieUtil {

    private static final String COOKIE_NAME = "slo_in_progress";

    /**
     * Sets the slo_in_progress cookie to indicate an ongoing SLO flow.
     */
    public static void setSloInProgress(HttpServletResponse response, int maxAge) {
        Cookie cookie = new Cookie(COOKIE_NAME, "true");
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        System.out.println("[CookieUtil] Set cookie: " + COOKIE_NAME + "=true");
    }

    /**
     * Clears the slo_in_progress cookie.
     */
    public static void clearSloInProgress(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        System.out.println("[CookieUtil] Cleared cookie: " + COOKIE_NAME);
    }

    /**
     * Checks if the slo_in_progress cookie is present and set to "true".
     */
    public static boolean isSloInProgress(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            if (COOKIE_NAME.equals(c.getName())) {
                return "true".equals(c.getValue());
            }
        }
        return false;
    }
}
