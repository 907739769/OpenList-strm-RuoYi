package com.ruoyi.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String secret = "mySecretKeyMustBeAtLeast256BitsLongForHS256Algorithm";

    /** 令牌过期时间（毫秒），默认2小时 */
    private long expiration = 7200000;

    /** 刷新令牌过期时间（毫秒），默认7天 */
    private long refreshExpiration = 604800000;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getExpiration() { return expiration; }
    public void setExpiration(long expiration) { this.expiration = expiration; }
    public long getRefreshExpiration() { return refreshExpiration; }
    public void setRefreshExpiration(long refreshExpiration) { this.refreshExpiration = refreshExpiration; }
}
