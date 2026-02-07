package com.ruoyi.openliststrm.task;

import com.ruoyi.openliststrm.domain.OpenlistCopyTask;
import com.ruoyi.openliststrm.domain.OpenlistStrmTask;
import com.ruoyi.openliststrm.domain.RenameTask;
import com.ruoyi.openliststrm.mapper.RenameTaskMapper;
import com.ruoyi.openliststrm.rename.RenameTaskManager;
import com.ruoyi.openliststrm.service.*;
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
    private IOpenlistCopyTaskService copyTaskService;

    @Autowired
    private IOpenlistStrmTaskService strmTaskService;

    @Autowired
    private ICopyService copyService;

    @Autowired
    private IStrmService strmService;

    @Autowired
    private RenameTaskManager renameTaskManager;

    @Autowired
    private IRenameTaskService renameTaskService;

    public void copy() {
        OpenlistCopyTask query = new OpenlistCopyTask();
        query.setCopyTaskStatus("1");
        List<OpenlistCopyTask> taskList = copyTaskService.selectOpenlistCopyTaskList(query);
        taskList.forEach(task -> {
            copyService.syncFiles(task.getCopyTaskSrc(), task.getCopyTaskDst());
        });
    }

    public void strm() {
        OpenlistStrmTask query = new OpenlistStrmTask();
        query.setStrmTaskStatus("1");
        List<OpenlistStrmTask> taskList = strmTaskService.selectOpenlistStrmTaskList(query);
        taskList.forEach(task -> {
            strmService.strmDir(task.getStrmTaskPath());
        });
    }

    public void rename() {
        RenameTask query = new RenameTask();
        query.setStatus("1");
        List<RenameTask> taskList = renameTaskService.selectRenameTaskList(query);
        taskList.forEach(task -> {
            renameTaskManager.executeTaskNow(task.getId());
        });
    }

}
