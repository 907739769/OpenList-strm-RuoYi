package com.ruoyi.framework.shiro.web.filter.online;

import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.enums.OnlineStatus;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.framework.shiro.session.OnlineSession;
import com.ruoyi.framework.shiro.session.OnlineSessionDAO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;

public class OnlineSessionFilter implements jakarta.servlet.Filter {

    private static final Logger log = LoggerFactory.getLogger(OnlineSessionFilter.class);
    @Value("${shiro.user.loginUrl}")
    private String loginUrl;
    private OnlineSessionDAO onlineSessionDAO;

    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }
    public void setOnlineSessionDAO(OnlineSessionDAO onlineSessionDAO) { this.onlineSessionDAO = onlineSessionDAO; }

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            Subject subject = org.apache.shiro.SecurityUtils.getSubject();
            String path = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
            log.info("OnlineSessionFilter path={}, subject={}", path, subject);

            if (subject == null || subject.getSession() == null) {
                chain.doFilter(request, response);
                return;
            }

            Session session = null;
            try {
                session = onlineSessionDAO.readSession(subject.getSession().getId());
            } catch (Exception e) {
                chain.doFilter(request, response);
                return;
            }

            log.info("OnlineSessionFilter readSession result={}, sessionId={}", session, subject.getSession().getId());
            if (session != null && session instanceof OnlineSession) {
                OnlineSession onlineSession = (OnlineSession) session;
                request.setAttribute(ShiroConstants.ONLINE_SESSION, onlineSession);
                boolean isGuest = onlineSession.getUserId() == null || onlineSession.getUserId() == 0L;
                if (isGuest) {
                    SysUser user = ShiroUtils.getSysUser();
                    log.info("OnlineSessionFilter isGuest={}, user={}", isGuest, user);
                    if (user != null) {
                        onlineSession.setUserId(user.getUserId());
                        onlineSession.setLoginName(user.getLoginName());
                        onlineSession.setAvatar(user.getAvatar());
                        onlineSession.setDeptName(user.getDept().getDeptName());
                        onlineSession.markAttributeChanged();
                        onlineSessionDAO.update(onlineSession);
                    }
                }

                if (onlineSession.getStatus() == OnlineStatus.off_line) {
                    subject.logout();
                    jakarta.servlet.http.HttpServletResponse httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
                    httpResponse.sendRedirect(loginUrl);
                    return;
                }
            }
            chain.doFilter(request, response);
        } catch (org.apache.shiro.UnavailableSecurityManagerException e) {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }
}
