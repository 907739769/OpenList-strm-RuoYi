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
}
