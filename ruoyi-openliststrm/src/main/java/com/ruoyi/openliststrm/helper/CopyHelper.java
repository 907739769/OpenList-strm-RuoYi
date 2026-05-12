package com.ruoyi.openliststrm.helper;

import com.ruoyi.common.utils.bean.BeanUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/17 11:07
 * @Version 1.0.0
 */
@Component
public class CopyHelper {

    private static final Object LOCK = new Object();

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    public void addCopy(OpenlistCopyPlus openlistCopyPlus) {
        AsyncManager.me().execute(() -> {
                //加锁 简单解决并发情况插入重复数据
                synchronized (LOCK) {
                    //保存或者更新
                    List<OpenlistCopyPlus> copyList = openlistCopyPlusService.lambdaQuery()
                            .eq(OpenlistCopyPlus::getCopySrcPath,openlistCopyPlus.getCopySrcPath())
                            .eq(OpenlistCopyPlus::getCopySrcFileName,openlistCopyPlus.getCopySrcFileName())
                            .list();
                    if (!CollectionUtils.isEmpty(copyList)) {
                        OpenlistCopyPlus newCopy = copyList.get(0);
                        int id = newCopy.getCopyId();
                        BeanUtils.copyProperties(openlistCopyPlus, newCopy);
                        newCopy.setCopyId(id);
                        openlistCopyPlusService.updateById(newCopy);
                    } else {
                        openlistCopyPlusService.save(openlistCopyPlus);
                    }
                }
            });
    }

    public boolean exitCopy(OpenlistCopyPlus openlistCopyPlus) {
        return openlistCopyPlusService.lambdaQuery()
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopySrcPath()), OpenlistCopyPlus::getCopySrcPath, openlistCopyPlus.getCopySrcPath())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopyDstPath()), OpenlistCopyPlus::getCopyDstPath, openlistCopyPlus.getCopyDstPath())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopySrcFileName()), OpenlistCopyPlus::getCopySrcFileName, openlistCopyPlus.getCopySrcFileName())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopyDstFileName()), OpenlistCopyPlus::getCopyDstFileName, openlistCopyPlus.getCopyDstFileName())
                .in(OpenlistCopyPlus::getCopyStatus, "1", "3")
                .count() > 0;
    }

}
