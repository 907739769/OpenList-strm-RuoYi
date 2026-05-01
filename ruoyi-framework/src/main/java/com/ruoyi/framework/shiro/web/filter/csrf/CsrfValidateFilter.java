package com.ruoyi.framework.shiro.web.filter.csrf;

import com.ruoyi.common.constant.ShiroConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import java.io.IOException;
import java.util.List;

public class CsrfValidateFilter implements Filter {

    private List<String> csrfWhites;
    private boolean enabled = false;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (!enabled) {
                chain.doFilter(request, response);
                return;
            }

            HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String method = httpServletRequest.getMethod();
            if (!"POST".equalsIgnoreCase(method)) {
                chain.doFilter(request, response);
                return;
            }

            if (StringUtils.matches(httpServletRequest.getServletPath(), csrfWhites)) {
                chain.doFilter(request, response);
                return;
            }

            String requestToken = httpServletRequest.getHeader(ShiroConstants.X_CSRF_TOKEN);
            Object sessionToken = null;
            try {
                Subject subject = SecurityUtils.getSubject();
                if (subject.getSession() != null) {
                    sessionToken = subject.getSession().getAttribute(ShiroConstants.CSRF_TOKEN);
                }
            } catch (Exception e) {
            }

            String sessionTokenStr = Convert.toStr(sessionToken, "");
            if (StringUtils.isEmpty(requestToken) || !requestToken.equalsIgnoreCase(sessionTokenStr)) {
                ServletUtils.renderString(httpResponse, "{\"code\":\"1\",\"msg\":\"当前请求的安全验证未通过，请刷新页面后重试。\"}");
                return;
            }

            chain.doFilter(request, response);
        } catch (org.apache.shiro.UnavailableSecurityManagerException e) {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    public List<String> getCsrfWhites() { return csrfWhites; }
    public void setCsrfWhites(List<String> csrfWhites) { this.csrfWhites = csrfWhites; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
