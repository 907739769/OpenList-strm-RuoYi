package com.ruoyi.openliststrm.helper;

import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.domain.OpenlistCopy;
import com.ruoyi.openliststrm.service.IOpenlistCopyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.TimerTask;

/**
 * @Author Jack
 * @Date 2025/7/17 11:07
 * @Version 1.0.0
 */
@Component
public class CopyHelper {

    @Autowired
    private IOpenlistCopyService copyService;

    public void addCopy(OpenlistCopy openlistCopy) {
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                OpenlistCopy query = new OpenlistCopy();
                query.setCopySrcPath(openlistCopy.getCopySrcPath());
                query.setCopySrcFileName(openlistCopy.getCopySrcFileName());
                query.setCopyDstPath(openlistCopy.getCopyDstPath());
                query.setCopyDstFileName(openlistCopy.getCopyDstFileName());
                List<OpenlistCopy> copyList = copyService.selectOpenlistCopyList(query);
                if (!CollectionUtils.isEmpty(copyList)) {
                    OpenlistCopy newCopy = copyList.get(0);
                    int id = newCopy.getCopyId();
                    BeanUtils.copyProperties(openlistCopy, newCopy);
                    newCopy.setCopyId(id);
                    copyService.updateOpenlistCopy(newCopy);
                } else {
                    copyService.insertOpenlistCopy(openlistCopy);
                }
            }
        });
    }

    public boolean exitCopy(OpenlistCopy openlistCopy) {
        OpenlistCopy query = new OpenlistCopy();
        query.setCopySrcPath(openlistCopy.getCopySrcPath());
        query.setCopySrcFileName(openlistCopy.getCopySrcFileName());
        query.setCopyDstPath(openlistCopy.getCopyDstPath());
        query.setCopyDstFileName(openlistCopy.getCopyDstFileName());
        List<OpenlistCopy> copyList = copyService.selectOpenlistCopyList(query);
        if (CollectionUtils.isEmpty(copyList)) {
            return false;
        }
        for (OpenlistCopy copy : copyList) {
            if ("1".equals(copy.getCopyStatus()) || "3".equals(copy.getCopyStatus())) {
                return true;
            }
        }
        return false;
    }

}
