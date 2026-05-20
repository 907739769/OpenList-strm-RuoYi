package com.ruoyi.common.utils;

import com.ruoyi.common.core.domain.entity.SysUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class CurrentUserService {

    public String getLoginName() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof SysUser user) {
                    return user.getLoginName();
                }
                if (principal instanceof String s) {
                    return s;
                }
                if (principal instanceof User user) {
                    return user.getUsername();
                }
            }
        } catch (Exception e) { /* Spring Security context not available */ }
        try { return ShiroUtils.getLoginName(); } catch (Exception e) { return "anonymous"; }
    }

    public Long getUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof SysUser user) {
                    return user.getUserId();
                }
            }
        } catch (Exception e) { /* Spring Security context not available */ }
        try { return ShiroUtils.getUserId(); } catch (Exception e) { return 0L; }
    }

    public SysUser getUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof SysUser user) {
                    return user;
                }
            }
        } catch (Exception e) { /* Spring Security context not available */ }
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attrs.getRequest();
            Object user = request.getAttribute("currentUser");
            if (user instanceof SysUser sysUser) {
                return sysUser;
            }
        } catch (Exception e) { }
        try { return ShiroUtils.getSysUser(); } catch (Exception e) { return null; }
    }
}
