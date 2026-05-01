package com.ruoyi.framework.shiro.web.filter;

import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.MessageUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.service.ISysUserOnlineService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.session.SessionException;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LogoutFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(LogoutFilter.class);
    private String loginUrl;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        httpResponse.setHeader("Pragma", "no-cache");
        httpResponse.setHeader("Expires", "0");

        try {
            Subject subject = org.apache.shiro.SecurityUtils.getSubject();
            String redirectUrl = loginUrl != null ? loginUrl : "/login";

            try {
                SysUser user = ShiroUtils.getSysUser();
                if (StringUtils.isNotNull(user)) {
                    String loginName = user.getLoginName();
                    AsyncManager.me().execute(AsyncFactory.recordLogininfor(loginName, Constants.LOGOUT, MessageUtils.message("user.logout.success")));
                    SpringUtils.getBean(ISysUserOnlineService.class).removeUserCache(loginName, ShiroUtils.getSessionId());
                }
                subject.logout();
            } catch (SessionException ise) {
                log.error("logout fail.", ise);
            }

            httpResponse.sendRedirect(redirectUrl);
        } catch (Exception e) {
            log.error("Encountered session exception during logout.", e);
            httpResponse.sendRedirect(loginUrl != null ? loginUrl : "/login");
        }
    }

    @Override
    public void destroy() {
    }

    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }
}
