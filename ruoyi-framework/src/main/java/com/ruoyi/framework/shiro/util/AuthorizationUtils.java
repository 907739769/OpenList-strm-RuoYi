package com.ruoyi.framework.shiro.util;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.RealmSecurityManager;
import com.ruoyi.framework.shiro.realm.UserRealm;

/**
 * 用户授权信息
 * 
 * @author ruoyi
 */
public class AuthorizationUtils
{
    /**
     * 清理所有用户授权信息缓存
     */
    public static void clearAllCachedAuthorizationInfo()
    {
        try {
            UserRealm realm = getUserRealm();
            if (realm != null) {
                realm.clearAllCachedAuthorizationInfo();
            }
        } catch (Exception e) {
            // Shiro cache not available (e.g., under JWT auth) — safe to ignore
        }
    }

    /**
     * 获取自定义Realm
     */
    public static UserRealm getUserRealm()
    {
        Object securityManager = SecurityUtils.getSecurityManager();
        if (securityManager instanceof RealmSecurityManager) {
            RealmSecurityManager rsm = (RealmSecurityManager) securityManager;
            Iterable<org.apache.shiro.realm.Realm> realms = rsm.getRealms();
            for (org.apache.shiro.realm.Realm realm : realms) {
                if (realm instanceof UserRealm) {
                    return (UserRealm) realm;
                }
            }
        }
        return null;
    }
}
