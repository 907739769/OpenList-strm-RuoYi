package com.ruoyi.framework.shiro.web.jakarta;

import org.apache.shiro.web.mgt.DefaultWebSecurityManager;

public class JakartaDefaultWebSecurityManager extends DefaultWebSecurityManager {
    private JakartaSimpleCookie sessionIdCookie;
    private boolean sessionIdCookieEnabled = true;
    public JakartaDefaultWebSecurityManager() { super(); }
    public JakartaSimpleCookie getSessionIdCookie() { return sessionIdCookie; }
    public void setSessionIdCookie(JakartaSimpleCookie cookie) { this.sessionIdCookie = cookie; }
    public boolean isSessionIdCookieEnabled() { return sessionIdCookieEnabled; }
    public void setSessionIdCookieEnabled(boolean enabled) { this.sessionIdCookieEnabled = enabled; }
}
