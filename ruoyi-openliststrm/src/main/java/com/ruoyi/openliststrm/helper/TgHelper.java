package com.ruoyi.openliststrm.helper;

import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.tg.TgSendMsg;
import org.apache.commons.lang3.StringUtils;

/**
 * @Author Jack
 * @Date 2025/7/20 18:49
 * @Version 1.0.0
 */
public class TgHelper {

    /** 缓存的发送端 Bot 实例，避免高频告警场景下每次都新建一个（内部会建立自己的 HTTP 客户端）*/
    private static volatile TgSendMsg cachedBot;
    private static volatile String cachedToken;
    private static volatile String cachedUserId;

    public static void sendMsg(String msg) {
        OpenlistConfig config = SpringUtils.getBean(OpenlistConfig.class);
        String token = config.getOpenListTgToken();
        String userId = config.getOpenListTgUserId();
        if (StringUtils.isAnyBlank(token, userId)) {
            return;
        }
        getBot(token, userId).sendMsg(msg);
    }

    /** token/userId 在后台配置变更后会重建实例，其余情况复用缓存 */
    private static TgSendMsg getBot(String token, String userId) {
        TgSendMsg bot = cachedBot;
        if (bot != null && token.equals(cachedToken) && userId.equals(cachedUserId)) {
            return bot;
        }
        synchronized (TgHelper.class) {
            if (cachedBot == null || !token.equals(cachedToken) || !userId.equals(cachedUserId)) {
                cachedBot = new TgSendMsg(token, userId);
                cachedToken = token;
                cachedUserId = userId;
            }
            return cachedBot;
        }
    }

}
