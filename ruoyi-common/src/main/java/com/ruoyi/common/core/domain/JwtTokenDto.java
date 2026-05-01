package com.ruoyi.common.core.domain;

import lombok.Data;
import java.util.Map;

/**
 * JWT令牌响应DTO
 * 
 * @author ruoyi
 */
@Data
public class JwtTokenDto
{
    /** 令牌 */
    private String token;

    /** 用户ID */
    private Long userId;

    /** 登录名称 */
    private String loginName;

    /** 用户名称 */
    private String userName;

    /** 权限列表 */
    private Map<String, Object> permissions;

    /** 过期时间 */
    private Long expireTime;
}
