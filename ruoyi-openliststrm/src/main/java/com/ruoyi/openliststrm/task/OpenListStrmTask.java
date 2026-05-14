package com.ruoyi.openliststrm.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.domain.RenameTaskPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyTaskPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmTaskPlusService;
import com.ruoyi.openliststrm.mybatisplus.service.IRenameTaskPlusService;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
import com.ruoyi.openliststrm.service.ICopyService;
import com.ruoyi.openliststrm.service.IStrmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/17 18:46
 * @Version 1.0.0
 */
@Component("openListStrmTask")
public class OpenListStrmTask {

    @Autowired
    private IOpenlistCopyTaskPlusService copyTaskPlusService;

    @Autowired
    private IOpenlistStrmTaskPlusService strmTaskPlusService;

    @Autowired
    private ICopyService copyService;

    @Autowired
    private IStrmService strmService;

    @Autowired
    private RenameTaskManager renameTaskManager;

    @Autowired
    private IRenameTaskPlusService renameTaskPlusService;

    public void copy() {
        LambdaQueryWrapper<OpenlistCopyTaskPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenlistCopyTaskPlus::getCopyTaskStatus, "1");
        List<OpenlistCopyTaskPlus> taskList = copyTaskPlusService.list(wrapper);
        taskList.forEach(task -> {
            copyService.syncFiles(task.getCopyTaskSrc(), task.getCopyTaskDst());
        });
    }

    public void strm() {
        LambdaQueryWrapper<OpenlistStrmTaskPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpenlistStrmTaskPlus::getStrmTaskStatus, "1");
        List<OpenlistStrmTaskPlus> taskList = strmTaskPlusService.list(wrapper);
        taskList.forEach(task -> {
            strmService.strmDir(task.getStrmTaskPath());
        });
    }

    public void rename() {
        LambdaQueryWrapper<RenameTaskPlus> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RenameTaskPlus::getStatus, "1");
        List<RenameTaskPlus> taskList = renameTaskPlusService.list(wrapper);
        taskList.forEach(task -> {
            renameTaskManager.executeTaskNow(task.getId());
        });
    }

}
