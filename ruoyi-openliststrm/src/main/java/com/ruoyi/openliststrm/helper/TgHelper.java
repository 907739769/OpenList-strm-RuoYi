package com.ruoyi.openliststrm.helper;

import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.tg.TgSendMsg;

/**
 * @Author Jack
 * @Date 2025/7/20 18:49
 * @Version 1.0.0
 */
public class TgHelper {

    public static void sendMsg(String msg) {
        OpenlistConfig config = SpringUtils.getBean(OpenlistConfig.class);
        TgSendMsg sendMsg = new TgSendMsg(config.getOpenListTgToken(), config.getOpenListTgUserId());
        sendMsg.sendMsg(msg);
    }

}
