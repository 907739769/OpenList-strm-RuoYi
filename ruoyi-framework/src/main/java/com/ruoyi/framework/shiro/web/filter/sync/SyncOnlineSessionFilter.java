package com.ruoyi.framework.shiro.web.filter.sync;

import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.framework.shiro.session.OnlineSession;
import com.ruoyi.framework.shiro.session.OnlineSessionDAO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import java.io.IOException;

public class SyncOnlineSessionFilter implements jakarta.servlet.Filter {

    private OnlineSessionDAO onlineSessionDAO;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        OnlineSession session = (OnlineSession) request.getAttribute(ShiroConstants.ONLINE_SESSION);
        if (session != null && session.getUserId() != null && session.getStopTimestamp() == null) {
            onlineSessionDAO.syncToDb(session);
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    public void setOnlineSessionDAO(OnlineSessionDAO onlineSessionDAO) { this.onlineSessionDAO = onlineSessionDAO; }
}
