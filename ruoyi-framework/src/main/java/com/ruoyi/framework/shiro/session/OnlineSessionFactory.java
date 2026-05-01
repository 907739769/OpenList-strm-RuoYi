package com.ruoyi.framework.shiro.session;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.mgt.SessionContext;
import org.apache.shiro.session.mgt.SessionFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.IpUtils;
import com.ruoyi.common.utils.http.UserAgentUtils;

@Component
public class OnlineSessionFactory implements SessionFactory
{
    @Override
    public Session createSession(SessionContext initData)
    {
        OnlineSession session = new OnlineSession();
        if (initData != null && initData instanceof JakartaWebSessionContext)
        {
            JakartaWebSessionContext sessionContext = (JakartaWebSessionContext) initData;
            HttpServletRequest request = (HttpServletRequest) sessionContext.getServletRequest();
            if (request != null)
            {
                String userAgent = request.getHeader("User-Agent");
                String os = UserAgentUtils.getOperatingSystem(userAgent);
                String browser = UserAgentUtils.getBrowser(userAgent);
                session.setHost(IpUtils.getIpAddr(request));
                session.setBrowser(browser);
                session.setOs(os);
            }
        }
        return session;
    }
}
