package com.ruoyi.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT配置属性
 * 
 * @author ruoyi
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfigProperties
{
    /** 密钥（至少256位用于HS256算法） */
    private String secret;

    /** 令牌过期时间（毫秒），默认2小时 */
    private long expiration = 7200000;

    /** 刷新令牌过期时间（毫秒），默认7天 */
    private long refreshExpiration = 604800000;

    public String getSecret() {
        if (secret == null || secret.isBlank()) {
            secret = generateRandomSecret();
        }
        return secret;
    }

    public void setSecret(String secret) { this.secret = secret; }
    public long getExpiration() { return expiration; }
    public void setExpiration(long expiration) { this.expiration = expiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
    public void setRefreshExpiration(long refreshExpiration) { this.refreshExpiration = refreshExpiration; }

    /** 生成随机密钥（64字节Base64编码） */
    private static String generateRandomSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
