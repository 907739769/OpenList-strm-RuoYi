package com.ruoyi.openliststrm.tg;

import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.service.ICopyService;
import com.ruoyi.openliststrm.service.IStrmService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.abilitybots.api.db.DBContext;
import org.telegram.abilitybots.api.sender.MessageSender;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Slf4j
public class ResponseHandler {

    private final MessageSender sender;

    public ResponseHandler(MessageSender sender, DBContext db) {
        this.sender = sender;
    }

    /**
     * 根据用户的输入同步
     *
     * @param chatId
     * @param parameter
     * @param messageId
     */
    public void replyToStrmDir(long chatId, String parameter, Integer messageId) {
        try {
            sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("==开始执行指定路径strm任务==").build());
            IStrmService strmService = SpringUtils.getBean("strmService");
            strmService.strmDir(parameter);
            sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("==执行指定路径strm任务完成==").build());
        } catch (Exception e) {
            log.error("", e);
        }
    }

    public void replyToSyncDir(long chatId, String parameter, Integer messageId) {
        try {
            if (!parameter.contains("#")) {
                sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("请输入正确的参数，例如：/阿里云盘/电影#/115网盘/电影").build());
            }
            String[] strings = parameter.split("#");
            if (strings.length != 2) {
                sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("请输入正确的参数，例如：/阿里云盘/电影#/115网盘/电影").build());
            }
            sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("==开始执行指定路径sync任务==").build());
            ICopyService copyService = SpringUtils.getBean("copyService");
            copyService.syncFiles(strings[0], strings[1]);
            sender.execute(SendMessage.builder().chatId(chatId).replyToMessageId(messageId).text("==执行指定路径sync任务完成==").build());
        } catch (Exception e) {
            log.error("", e);
        }
    }


}
