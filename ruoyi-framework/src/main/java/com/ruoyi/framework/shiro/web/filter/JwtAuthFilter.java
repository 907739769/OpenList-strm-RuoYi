package com.ruoyi.framework.shiro.web.filter;

import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.JwtTokenUtil;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.ISysUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthFilter implements jakarta.servlet.Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ISysUserService userService;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/captchaImage")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.isNotEmpty(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());
            try {
                String username = jwtTokenUtil.getUsernameFromToken(token);
                if (StringUtils.isNotEmpty(username)) {
                    SysUser user = userService.selectUserByLoginName(username);
                    if (user != null) {
                        request.setAttribute("currentUser", username);
                        chain.doFilter(request, response);
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("JWT token validation failed: {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
