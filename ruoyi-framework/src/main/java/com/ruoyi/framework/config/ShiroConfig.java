package com.ruoyi.framework.config;

import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.shiro.realm.UserRealm;
import com.ruoyi.framework.shiro.session.OnlineSessionDAO;
import com.ruoyi.framework.shiro.session.OnlineSessionFactory;
import com.ruoyi.framework.shiro.web.filter.LogoutFilter;
import com.ruoyi.framework.shiro.web.filter.JwtAuthFilter;
import com.ruoyi.framework.shiro.web.jakarta.JakartaDefaultWebSecurityManager;
import com.ruoyi.framework.shiro.web.session.OnlineWebSessionManager;
import com.ruoyi.framework.shiro.web.session.SpringSessionValidationScheduler;
import org.apache.shiro.web.util.WebUtils;
import org.apache.commons.io.IOUtils;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.mgt.SecurityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class ShiroConfig
{
    @Value("${shiro.session.expireTime}")
    private int expireTime;

    @Value("${shiro.session.validationInterval}")
    private int validationInterval;

    @Value("${shiro.session.maxSession}")
    private int maxSession;

    @Value("${shiro.session.kickoutAfter}")
    private boolean kickoutAfter;

    @Value("${shiro.user.captchaEnabled}")
    private boolean captchaEnabled;

    @Value("${shiro.user.captchaType}")
    private String captchaType;

    @Value("${shiro.cookie.domain}")
    private String domain;

    @Value("${shiro.cookie.path}")
    private String path;

    @Value("${shiro.cookie.httpOnly}")
    private boolean httpOnly;

    @Value("${shiro.cookie.maxAge}")
    private int maxAge;

    @Value("${shiro.cookie.cipherKey}")
    private String cipherKey;

    @Value("${shiro.user.loginUrl}")
    private String loginUrl;

    @Value("${shiro.user.unauthorizedUrl}")
    private String unauthorizedUrl;

    @Value("${shiro.rememberMe.enabled: false}")
    private boolean rememberMe;

    @Value("${csrf.enabled: false}")
    private boolean csrfEnabled;

    @Value("${csrf.whites: ''}")
    private String csrfWhites;

    @Bean
    public MemoryConstrainedCacheManager getCacheManager()
    {
        return new MemoryConstrainedCacheManager();
    }

    protected InputStream getCacheManagerConfigFileInputStream()
    {
        String configFile = "ehcache/ehcache-shiro.xml";
        InputStream inputStream = null;
        try
        {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(configFile);
            byte[] b = IOUtils.toByteArray(resource.getInputStream());
            InputStream in = new ByteArrayInputStream(b);
            return in;
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Unable to obtain input stream for cacheManagerConfigFile [" + configFile + "]", e);
        }
        finally
        {
            IOUtils.closeQuietly(inputStream);
        }
    }

    @Bean
    public UserRealm userRealm(MemoryConstrainedCacheManager cacheManager)
    {
        UserRealm userRealm = new UserRealm();
        userRealm.setAuthorizationCacheName(Constants.SYS_AUTH_CACHE);
        userRealm.setCacheManager(cacheManager);
        return userRealm;
    }

    @Bean
    public OnlineSessionDAO sessionDAO()
    {
        return new OnlineSessionDAO();
    }

    @Bean
    public OnlineSessionFactory sessionFactory()
    {
        return new OnlineSessionFactory();
    }

    @Bean
    public OnlineWebSessionManager sessionManager()
    {
        OnlineWebSessionManager manager = new OnlineWebSessionManager();
        manager.setCacheManager(getCacheManager());
        manager.setDeleteInvalidSessions(true);
        manager.setGlobalSessionTimeout(expireTime * 60 * 1000);
        manager.setSessionIdUrlRewritingEnabled(false);
        manager.setSessionValidationScheduler(SpringUtils.getBean(SpringSessionValidationScheduler.class));
        manager.setSessionValidationSchedulerEnabled(true);
        manager.setSessionDAO(sessionDAO());
        manager.setSessionFactory(sessionFactory());
        return manager;
    }

    @Bean
    public SecurityManager securityManager(UserRealm userRealm)
    {
        JakartaDefaultWebSecurityManager securityManager = new JakartaDefaultWebSecurityManager();
        securityManager.setRealm(userRealm);
        securityManager.setCacheManager(getCacheManager());
        securityManager.setSessionManager(sessionManager());
        return securityManager;
    }

    @Bean
    public LogoutFilter logoutFilter()
    {
        LogoutFilter filter = new LogoutFilter();
        filter.setLoginUrl(loginUrl);
        return filter;
    }

    @Bean
    public FilterRegistrationBean<LogoutFilter> logoutFilterRegistration(LogoutFilter filter)
    {
        FilterRegistrationBean<LogoutFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/logout");
        registration.setName("logout");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter)
    {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setName("jwtAuth");
        registration.setOrder(7);
        return registration;
    }

    /**
     * Bind SecurityManager to ServletContext for SecurityUtils.getSecurityManager()
     */
    @Bean
    public ServletContextInitializer securityManagerInitializer(SecurityManager securityManager)
    {
        return new ServletContextInitializer()
        {
            @Override
            public void onStartup(ServletContext servletContext) throws ServletException
            {
                servletContext.setAttribute(org.apache.shiro.web.util.WebUtils.class.getPackage().getName() + ".SecurityManager", securityManager);
                // Also set via SecurityUtils key
                servletContext.setAttribute("shiroSecurityManager", securityManager);
            }
        };
    }
}
