package com.ruoyi.openliststrm.helper;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistCopyPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistCopyPlusService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CopyHelper {

    @Autowired
    private IOpenlistCopyPlusService openlistCopyPlusService;

    public void addCopy(OpenlistCopyPlus openlistCopyPlus) {
        AsyncManager.me().execute(() -> {
            try {
                OpenlistCopyPlus existing = openlistCopyPlusService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpenlistCopyPlus>()
                                .eq(OpenlistCopyPlus::getCopySrcPath, openlistCopyPlus.getCopySrcPath())
                                .eq(OpenlistCopyPlus::getCopySrcFileName, openlistCopyPlus.getCopySrcFileName())
                                .in(OpenlistCopyPlus::getCopyStatus, "1", "2", "3")
                );
                if (existing != null) {
                    openlistCopyPlus.setCopyId(existing.getCopyId());
                    openlistCopyPlus.setCopyTaskId(existing.getCopyTaskId());
                    openlistCopyPlusService.updateById(openlistCopyPlus);
                } else {
                    openlistCopyPlusService.save(openlistCopyPlus);
                }
            } catch (MybatisPlusException e) {
                if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                    log.debug("Copy record already exists: path={}, fileName={}",
                            openlistCopyPlus.getCopySrcPath(), openlistCopyPlus.getCopySrcFileName());
                } else {
                    log.error("Error adding copy: path={}, fileName={}",
                            openlistCopyPlus.getCopySrcPath(), openlistCopyPlus.getCopySrcFileName(), e);
                }
            } catch (Exception e) {
                log.error("Error adding copy: path={}, fileName={}",
                        openlistCopyPlus.getCopySrcPath(), openlistCopyPlus.getCopySrcFileName(), e);
            }
        });
    }

    /**
     * 检查copy记录是否已存在
     */
    public boolean existsCopy(OpenlistCopyPlus openlistCopyPlus) {
        return openlistCopyPlusService.lambdaQuery()
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopySrcPath()), OpenlistCopyPlus::getCopySrcPath, openlistCopyPlus.getCopySrcPath())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopyDstPath()), OpenlistCopyPlus::getCopyDstPath, openlistCopyPlus.getCopyDstPath())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopySrcFileName()), OpenlistCopyPlus::getCopySrcFileName, openlistCopyPlus.getCopySrcFileName())
                .eq(StringUtils.isNotBlank(openlistCopyPlus.getCopyDstFileName()), OpenlistCopyPlus::getCopyDstFileName, openlistCopyPlus.getCopyDstFileName())
                .in(OpenlistCopyPlus::getCopyStatus, "1", "3")
                .count() > 0;
    }

}
