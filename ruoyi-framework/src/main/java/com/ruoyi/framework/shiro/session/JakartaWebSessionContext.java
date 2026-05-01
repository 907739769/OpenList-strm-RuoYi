package com.ruoyi.framework.shiro.session;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

public interface JakartaWebSessionContext {
    ServletRequest getServletRequest();
    ServletResponse getServletResponse();
}
