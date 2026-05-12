package com.ruoyi.framework.manager.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.AddressUtils;
import com.ruoyi.common.utils.LogUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.http.UserAgentUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.framework.shiro.session.OnlineSession;
import com.ruoyi.system.domain.SysLogininfor;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.domain.SysUserOnline;
import com.ruoyi.system.service.ISysOperLogService;
import com.ruoyi.system.service.ISysUserOnlineService;
import com.ruoyi.system.service.impl.SysLogininforServiceImpl;

/**
 * 异步工厂（产生任务用）
 * 
 * @author liuhulu
 *
 */
public class AsyncFactory
{
    private static final Logger sys_user_logger = LoggerFactory.getLogger("sys-user");

    public static Runnable syncSessionToDb(final OnlineSession session)
    {
        return () -> {
            SysUserOnline online = new SysUserOnline();
            online.setSessionId(String.valueOf(session.getId()));
            online.setDeptName(session.getDeptName());
            online.setLoginName(session.getLoginName());
            online.setStartTimestamp(session.getStartTimestamp());
            online.setLastAccessTime(session.getLastAccessTime());
            online.setExpireTime(session.getTimeout());
            online.setIpaddr(session.getHost());
            online.setLoginLocation(AddressUtils.getRealAddressByIP(session.getHost()));
            online.setBrowser(session.getBrowser());
            online.setOs(session.getOs());
            online.setStatus(session.getStatus());
            SpringUtils.getBean(ISysUserOnlineService.class).saveOnline(online);
        };
    }

    public static Runnable recordOper(final SysOperLog operLog)
    {
        return () -> {
            operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
            SpringUtils.getBean(ISysOperLogService.class).insertOperlog(operLog);
        };
    }

    public static Runnable recordLogininfor(final String username, final String status, final String message, final Object... args)
    {
        final String userAgent = ServletUtils.getRequest().getHeader("User-Agent");
        final String ip = ShiroUtils.getIp();
        return () -> {
            String address = AddressUtils.getRealAddressByIP(ip);
            StringBuilder s = new StringBuilder();
            s.append(LogUtils.getBlock(ip));
            s.append(address);
            s.append(LogUtils.getBlock(username));
            s.append(LogUtils.getBlock(status));
            s.append(LogUtils.getBlock(message));
            sys_user_logger.info(s.toString(), args);
            String os = UserAgentUtils.getOperatingSystem(userAgent);
            String browser = UserAgentUtils.getBrowser(userAgent);
            SysLogininfor logininfor = new SysLogininfor();
            logininfor.setLoginName(username);
            logininfor.setIpaddr(ip);
            logininfor.setLoginLocation(address);
            logininfor.setBrowser(browser);
            logininfor.setOs(os);
            logininfor.setMsg(message);
            if (StringUtils.equalsAny(status, Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER))
            {
                logininfor.setStatus(Constants.SUCCESS);
            }
            else if (Constants.LOGIN_FAIL.equals(status))
            {
                logininfor.setStatus(Constants.FAIL);
            }
            SpringUtils.getBean(SysLogininforServiceImpl.class).insertLogininfor(logininfor);
        };
    }
}
