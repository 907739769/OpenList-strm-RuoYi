package com.ruoyi.openliststrm.helper;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StrmHelper {

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    public void addStrm(String strmPath, String strmFileName, String status) {
        AsyncManager.me().execute(() -> {
            try {
                OpenlistStrmPlus existing = openlistStrmPlusService.getOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OpenlistStrmPlus>()
                                .eq(OpenlistStrmPlus::getStrmPath, strmPath)
                                .eq(OpenlistStrmPlus::getStrmFileName, strmFileName)
                                .in(OpenlistStrmPlus::getStrmStatus, "0", "1")
                );
                OpenlistStrmPlus strm = new OpenlistStrmPlus();
                strm.setStrmPath(strmPath);
                strm.setStrmFileName(strmFileName);
                strm.setStrmStatus(status);
                if (existing != null) {
                    strm.setStrmId(existing.getStrmId());
                    openlistStrmPlusService.updateById(strm);
                } else {
                    openlistStrmPlusService.save(strm);
                }
            } catch (MybatisPlusException e) {
                if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                    log.debug("Strm record already exists: path={}, fileName={}", strmPath, strmFileName);
                } else {
                    log.error("Error adding strm: path={}, fileName={}", strmPath, strmFileName, e);
                }
            } catch (Exception e) {
                log.error("Error adding strm: path={}, fileName={}", strmPath, strmFileName, e);
            }
        });
    }

    /**
     * 判断strm的文件是否处理过
     */
    public boolean existsStrm(String strmPath, String strmFileName) {
        return openlistStrmPlusService.lambdaQuery()
                .eq(OpenlistStrmPlus::getStrmPath, strmPath)
                .eq(OpenlistStrmPlus::getStrmFileName, strmFileName)
                .eq(OpenlistStrmPlus::getStrmStatus, "1")
                .count() > 0;
    }
}
