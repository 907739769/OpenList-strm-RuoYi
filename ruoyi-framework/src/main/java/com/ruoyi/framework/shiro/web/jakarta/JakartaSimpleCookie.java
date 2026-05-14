package com.ruoyi.framework.shiro.web.jakarta;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JakartaSimpleCookie {
    private String name, value, domain, path;
    private int maxAge = -1, version = 0;
    private boolean httpOnly, secure;
    public JakartaSimpleCookie() { name = "default"; }
    public JakartaSimpleCookie(String name) { this.name = name; }
    public JakartaSimpleCookie(String name, String value) { this.name = name; this.value = value; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getMaxAge() { return maxAge; }
    public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
    public boolean isHttpOnly() { return httpOnly; }
    public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isSecure() { return secure; }
    public void setSecure(boolean secure) { this.secure = secure; }
    public void setValue(String value, HttpServletRequest request, HttpServletResponse response) {
        this.value = value; response.addCookie(toJakartaCookie());
    }
    public String getValue(HttpServletRequest request) {
        if (request == null) return null;
        for (Cookie c : request.getCookies() != null ? request.getCookies() : new Cookie[0])
            if (name.equals(c.getName())) return c.getValue();
        return null;
    }
    private Cookie toJakartaCookie() {
        Cookie c = new Cookie(name, value);
        if (domain != null) c.setDomain(domain);
        if (path != null) c.setPath(path);
        c.setMaxAge(maxAge); c.setHttpOnly(httpOnly); c.setVersion(version); c.setSecure(secure);
        return c;
    }
}
