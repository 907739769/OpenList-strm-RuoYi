package com.ruoyi.openliststrm.task;

import com.ruoyi.openliststrm.domain.OpenlistCopyTask;
import com.ruoyi.openliststrm.domain.OpenlistStrmTask;
import com.ruoyi.openliststrm.service.ICopyService;
import com.ruoyi.openliststrm.service.IOpenlistCopyTaskService;
import com.ruoyi.openliststrm.service.IOpenlistStrmTaskService;
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
    private IOpenlistCopyTaskService copyTaskService;

    @Autowired
    private IOpenlistStrmTaskService strmTaskService;

    @Autowired
    private ICopyService copyService;

    @Autowired
    private IStrmService strmService;

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

}
