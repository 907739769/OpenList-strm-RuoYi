package com.ruoyi.framework.shiro.web.jakarta;

import java.util.Base64;
import java.security.SecureRandom;

public class JakartaCookieRememberMeManager {
    private JakartaSimpleCookie cookie;
    private byte[] cipherKey;
    private static final SecureRandom random = new SecureRandom();
    public JakartaCookieRememberMeManager() {
        setCookie(new JakartaSimpleCookie("rememberMe"));
        setCipherKey(new byte[16]);
        random.nextBytes(cipherKey);
    }
    public JakartaSimpleCookie getCookie() { return cookie; }
    public void setCookie(JakartaSimpleCookie cookie) { this.cookie = cookie; }
    public void setCipherKey(byte[] cipherKey) { this.cipherKey = cipherKey; }
    public byte[] encrypt(byte[] serialized) {
        try { return Base64.getEncoder().encode(serialized); }
        catch (Exception e) { throw new RuntimeException("Error encrypting data", e); }
    }
    public byte[] decrypt(byte[] encrypted) {
        try { return Base64.getDecoder().decode(encrypted); }
        catch (Exception e) { throw new RuntimeException("Error decrypting data", e); }
    }
}
