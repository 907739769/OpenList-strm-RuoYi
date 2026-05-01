package com.ruoyi.framework.shiro.web.filter.kickout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.DefaultSessionKey;
import org.apache.shiro.session.mgt.SessionManager;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

public class KickoutSessionFilter implements jakarta.servlet.Filter {

    private final static ObjectMapper objectMapper = new ObjectMapper();
    private int maxSession = -1;
    private Boolean kickoutAfter = false;
    private String kickoutUrl;
    private SessionManager sessionManager;
    private Cache<String, Deque<Serializable>> cache;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (maxSession == -1) {
                chain.doFilter(request, response);
                return;
            }

            org.apache.shiro.subject.Subject subject = org.apache.shiro.SecurityUtils.getSubject();
            if (!subject.isAuthenticated() && !subject.isRemembered()) {
                chain.doFilter(request, response);
                return;
            }

            try {
                Session session = subject.getSession();
                SysUser user = ShiroUtils.getSysUser();
                String loginName = user.getLoginName();
                Serializable sessionId = session.getId();

                Deque<Serializable> deque = cache.get(loginName);
                if (deque == null) {
                    deque = new ArrayDeque<>();
                }

                if (!deque.contains(sessionId) && session.getAttribute("kickout") == null) {
                    deque.push(sessionId);
                    cache.put(loginName, deque);
                }

                while (deque.size() > maxSession) {
                    Serializable kickoutSessionId = kickoutAfter ? deque.removeFirst() : deque.removeLast();
                    cache.put(loginName, deque);

                    try {
                        Session kickoutSession = sessionManager.getSession(new DefaultSessionKey(kickoutSessionId));
                        if (null != kickoutSession) {
                            kickoutSession.setAttribute("kickout", true);
                        }
                    } catch (Exception e) {
                    }
                }

                if (session.getAttribute("kickout") != null && (Boolean) session.getAttribute("kickout") == true) {
                    subject.logout();
                    HttpServletRequest req = (HttpServletRequest) request;
                    HttpServletResponse res = (HttpServletResponse) response;
                    if (ServletUtils.isAjaxRequest(req)) {
                        ServletUtils.renderString(res, objectMapper.writeValueAsString(AjaxResult.error("您已在别处登录，请您修改密码或重新登录")));
                    } else {
                        res.sendRedirect(kickoutUrl);
                    }
                    return;
                }
            } catch (Exception e) {
            }

            chain.doFilter(request, response);
        } catch (org.apache.shiro.UnavailableSecurityManagerException e) {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    public void setMaxSession(int maxSession) { this.maxSession = maxSession; }
    public void setKickoutAfter(boolean kickoutAfter) { this.kickoutAfter = kickoutAfter; }
    public void setKickoutUrl(String kickoutUrl) { this.kickoutUrl = kickoutUrl; }
    public void setSessionManager(SessionManager sessionManager) { this.sessionManager = sessionManager; }
    public void setCacheManager(CacheManager cacheManager) { this.cache = cacheManager.getCache(ShiroConstants.SYS_USERCACHE); }
}
