package com.ruoyi.openliststrm.helper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.TimerTask;

/**
 * @Author Jack
 * @Date 2025/7/16 20:50
 * @Version 1.0.0
 */
@Component
public class StrmHelper {

    @Autowired
    private IOpenlistStrmPlusService openlistStrmPlusService;

    /**
     * 添加strm
     *
     * @param strmPath
     * @param strmFileName
     * @param status
     */
    public void addStrm(String strmPath, String strmFileName, String status) {
        AsyncManager.me().execute(new TimerTask() {
            @Override
            public void run() {
                //保存或者更新
                OpenlistStrmPlus strm = new OpenlistStrmPlus();
                strm.setStrmPath(strmPath);
                strm.setStrmFileName(strmFileName);
                strm.setStrmStatus(status);
                openlistStrmPlusService.saveOrUpdate(strm,
                        Wrappers.<OpenlistStrmPlus>lambdaUpdate()
                                .eq(OpenlistStrmPlus::getStrmPath, strmPath)
                                .eq(OpenlistStrmPlus::getStrmFileName, strmFileName));
            }
        });
    }

    /**
     * 判断strm的文件是否处理过
     *
     * @param strmPath
     * @param strmFileName
     * @return
     */
    public boolean exitStrm(String strmPath, String strmFileName) {
        return openlistStrmPlusService.lambdaQuery().eq(OpenlistStrmPlus::getStrmPath, strmPath)
                .eq(OpenlistStrmPlus::getStrmFileName, strmFileName)
                .eq(OpenlistStrmPlus::getStrmStatus, "1")
                .count() > 0;
    }


}
