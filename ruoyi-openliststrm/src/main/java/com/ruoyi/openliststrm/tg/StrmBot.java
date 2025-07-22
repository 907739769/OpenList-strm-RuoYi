package com.ruoyi.openliststrm.tg;

import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.openliststrm.service.ICopyService;
import com.ruoyi.openliststrm.service.IStrmService;
import com.ruoyi.openliststrm.task.OpenListStrmTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.telegram.abilitybots.api.bot.AbilityBot;
import org.telegram.abilitybots.api.objects.Ability;
import org.telegram.abilitybots.api.objects.Flag;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

import static org.telegram.abilitybots.api.objects.Locality.USER;
import static org.telegram.abilitybots.api.objects.Privacy.CREATOR;
import static org.telegram.abilitybots.api.util.AbilityUtils.getChatId;

/**
 * @Author Jack
 * @Date 2024/6/4 21:33
 * @Version 1.0.0
 */
@Slf4j
public class StrmBot extends AbilityBot {

    private final String adminUserId;

    private final ResponseHandler responseHandler = new ResponseHandler(sender, db);

    public StrmBot(String botToken, String adminUserId) {
        super(botToken, "bot");
        this.adminUserId = adminUserId;
        registerCommands();
    }

    private void registerCommands() {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("/strm", "执行strm任务"));
        commands.add(new BotCommand("/strmdir", "生成指定路径strm"));
        commands.add(new BotCommand("/sync", "执行copy任务"));
        commands.add(new BotCommand("/syncdir", "同步openlist指定目录"));
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("注册tg命令失败", e);
        }
    }

    @Override
    public long creatorId() {
        return Long.parseLong(adminUserId);
    }

    public Ability strm() {
        return Ability.builder()
                .name("strm")
                .info("执行strm任务")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    silent.send("==开始执行strm任务==", ctx.chatId());
                    OpenListStrmTask openListStrmTask = SpringUtils.getBean("openListStrmTask");
                    openListStrmTask.strm();
                    silent.send("==执行strm任务完成==", ctx.chatId());
                })
                .build();
    }

    public Ability strmDir() {
        return Ability.builder()
                .name("strmdir")
                .info("生成指定路径strm")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    String parameter;
                    try {
                        parameter = ctx.firstArg();
                    } catch (Exception e) {
                        silent.forceReply("请输入路径", ctx.chatId());
                        return;
                    }
                    if (StringUtils.isBlank(parameter)) {
                        silent.forceReply("请输入路径", ctx.chatId());
                        return;
                    }
                    silent.send("==开始执行指定路径strm任务==", ctx.chatId());
                    IStrmService strmService = SpringUtils.getBean("strmService");
                    strmService.strmDir(parameter);
                    silent.send("==执行指定路径strm任务完成==", ctx.chatId());
                })
                .reply((bot, upd) -> responseHandler.replyToStrmDir(getChatId(upd), upd.getMessage().getText(), upd.getMessage().getMessageId()), Flag.REPLY,//回复
                        upd -> upd.getMessage().getReplyToMessage().hasText(), upd -> upd.getMessage().getReplyToMessage().getText().equals("请输入路径")//回复的是上面的问题
                )
                .build();
    }

    public Ability sync() {
        return Ability.builder()
                .name("sync")
                .info("执行copy任务")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    silent.send("==开始执行同步openlist任务==", ctx.chatId());
                    OpenListStrmTask openListStrmTask = SpringUtils.getBean("openListStrmTask");
                    openListStrmTask.copy();
                    silent.send("==执行同步openlist任务完成==", ctx.chatId());
                })
                .build();
    }

    public Ability syncDir() {
        return Ability.builder()
                .name("syncdir")
                .info("同步openlist指定目录")
                .privacy(CREATOR)
                .locality(USER)
                .input(0)
                .action(ctx -> {
                    String parameter;
                    try {
                        parameter = ctx.firstArg();
                    } catch (Exception e) {
                        silent.forceReply("请输入路径(格式：源路径#目标路径)", ctx.chatId());
                        return;
                    }
                    if (StringUtils.isBlank(parameter)) {
                        silent.forceReply("请输入路径(格式：源路径#目标路径)", ctx.chatId());
                        return;
                    }
                    if (!parameter.contains("#")) {
                        silent.send("请输入正确的参数，例如：/阿里云盘/电影#/115网盘/电影", ctx.chatId());
                    }
                    String[] strings = parameter.split("#");
                    if (strings.length != 2) {
                        silent.send("请输入正确的参数，例如：/阿里云盘/电影#/115网盘/电影", ctx.chatId());
                    }
                    silent.send("==开始执行同步openlist指定目录任务==", ctx.chatId());
                    ICopyService copyService = SpringUtils.getBean("copyService");
                    copyService.syncFiles(strings[0], strings[1]);
                    silent.send("==执行同步openlist指定目录任务完成==", ctx.chatId());
                })
                .reply((bot, upd) -> responseHandler.replyToSyncDir(getChatId(upd), upd.getMessage().getText(), upd.getMessage().getMessageId()), Flag.REPLY,//回复
                        upd -> upd.getMessage().getReplyToMessage().hasText(), upd -> upd.getMessage().getReplyToMessage().getText().equals("请输入路径(格式：源路径#目标路径)")//回复的是上面的问题
                )
                .build();
    }

}
