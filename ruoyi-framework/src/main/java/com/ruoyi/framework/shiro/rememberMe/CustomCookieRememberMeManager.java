package com.ruoyi.framework.shiro.rememberMe;

import org.apache.shiro.web.servlet.SimpleCookie;
import org.apache.shiro.web.mgt.CookieRememberMeManager;

public class CustomCookieRememberMeManager extends CookieRememberMeManager {

    private SimpleCookie cookie;

    public CustomCookieRememberMeManager() {
        this.cookie = new SimpleCookie("rememberMe");
    }

    public SimpleCookie getCookie() { return cookie; }
    public void setCookie(SimpleCookie cookie) { this.cookie = cookie; }
}
