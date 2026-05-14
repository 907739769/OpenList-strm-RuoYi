package com.ruoyi.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ruoyi.common.config.JwtConfigProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import io.jsonwebtoken.Jws;

/**
 * JWT工具类
 * 
 * @author ruoyi
 */
@Component
public class JwtTokenUtil
{
    @Autowired
    private JwtConfigProperties jwtConfig;

    /**
     * 生成令牌
     *
     * @param loginName 登录名称（作为subject）
     * @param userId 用户ID
     * @param claims 额外声明
     * @return 令牌
     */
    public String generateToken(String loginName, Long userId, Map<String, Object> claims)
    {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + jwtConfig.getExpiration());

        return Jwts.builder()
                .subject(loginName)
                .claims(claims)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(generateSecretKey())
                .compact();
    }

    /**
     * 解析并验证令牌
     *
     * @param token 令牌
     * @return 解析后的Claims
     */
    public Jws<Claims> parseToken(String token)
    {
        return Jwts.parser()
                .verifyWith(generateSecretKey())
                .build()
                .parseSignedClaims(token);
    }

    /**
     * 从令牌中获取用户名
     *
     * @param token 令牌
     * @return 用户名
     */
    public String getUsernameFromToken(String token)
    {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 从令牌中获取用户ID
     *
     * @param token 令牌
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token)
    {
        Claims claims = getClaimsFromToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number)
        {
            return ((Number) userId).longValue();
        }
        return Long.parseLong(userId.toString());
    }

    /**
     * 判断令牌是否过期
     *
     * @param token 令牌
     * @return 是否过期
     */
    public boolean isTokenExpired(String token)
    {
        try
        {
            Date expiration = getExpirationFromToken(token);
            return expiration.before(new Date());
        }
        catch (ExpiredJwtException e)
        {
            return true;
        }
    }

    /**
     * 验证令牌是否有效
     *
     * @param token 令牌
     * @param loginName 登录名称
     * @return 是否有效
     */
    public boolean isTokenValid(String token, String loginName)
    {
        try
        {
            String username = getUsernameFromToken(token);
            return username.equals(loginName) && !isTokenExpired(token);
        }
        catch (JwtException | IllegalArgumentException e)
        {
            return false;
        }
    }

    /**
     * 从令牌中获取过期时间
     *
     * @param token 令牌
     * @return 过期时间
     */
    public Date getExpirationFromToken(String token)
    {
        return getClaimsFromToken(token).getExpiration();
    }

    /**
     * 从令牌中获取声明
     *
     * @param token 令牌
     * @return 声明
     */
    private Claims getClaimsFromToken(String token)
    {
        return parseToken(token).getPayload();
    }

    /**
     * 生成HS256密钥
     *
     * @return SecretKey
     */
    public SecretKey generateSecretKey()
    {
        return new SecretKeySpec(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
