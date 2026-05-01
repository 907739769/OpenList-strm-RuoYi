package com.ruoyi.framework.shiro.web.filter.captcha;

import com.google.code.kaptcha.Constants;
import com.ruoyi.common.constant.ShiroConstants;
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

public class CaptchaValidateFilter implements Filter {

    private boolean captchaEnabled = true;
    private String captchaType = "math";

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setAttribute(ShiroConstants.CURRENT_ENABLED, captchaEnabled);
        request.setAttribute(ShiroConstants.CURRENT_TYPE, captchaType);

        if (!captchaEnabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        if (!"post".equals(httpServletRequest.getMethod().toLowerCase())) {
            chain.doFilter(request, response);
            return;
        }

        String validateCode = httpServletRequest.getParameter(ShiroConstants.CURRENT_VALIDATECODE);
        String code = null;
        try {
            Subject subject = SecurityUtils.getSubject();
            if (subject.getSession() != null) {
                code = String.valueOf(subject.getSession().getAttribute(Constants.KAPTCHA_SESSION_KEY));
            }
        } catch (Exception e) {
            code = "";
        }

        if (StringUtils.isEmpty(validateCode) || !validateCode.equalsIgnoreCase(code)) {
            request.setAttribute(ShiroConstants.CURRENT_CAPTCHA, ShiroConstants.CAPTCHA_ERROR);
            chain.doFilter(request, response);
            return;
        }

        try {
            Subject subject = SecurityUtils.getSubject();
            if (subject.getSession() != null) {
                subject.getSession().removeAttribute(Constants.KAPTCHA_SESSION_KEY);
            }
        } catch (Exception e) {
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    public void setCaptchaEnabled(boolean captchaEnabled) { this.captchaEnabled = captchaEnabled; }
    public void setCaptchaType(String captchaType) { this.captchaType = captchaType; }
}
