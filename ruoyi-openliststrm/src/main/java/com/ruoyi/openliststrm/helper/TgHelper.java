package com.ruoyi.openliststrm.helper;

import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.tg.TgSendMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author Jack
 * @Date 2025/7/20 18:49
 * @Version 1.0.0
 */
@Component
public class TgHelper {

    @Autowired
    private OpenlistConfig config;

    public void sendMsg(String msg) {
        TgSendMsg sendMsg = new TgSendMsg(config.getOpenListTgToken(), config.getOpenListTgUserId());
        sendMsg.sendMsg(msg);
    }

}
