<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head><title>SAML SSO PoC</title></head>
<body>
    <h2>SAML SSO — Proof of Concept</h2>
    <%
        String username = (String) session.getAttribute("username");
        String group = (String) session.getAttribute("group");
        String location = (String) session.getAttribute("location");
        if (username != null) {
    %>
        <p>Welcome, <%= username %>!</p>
        <p>Group: <%= group %></p>
        <p>Location: <%= location %></p>
        <a href="logoutsso">Logout</a>
    <%
        } else {
    %>
        <a href="loginsso">Login dengan SSO</a>
    <%
        }
    %>
</body>
</html>
