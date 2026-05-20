package com.ruoyi.openliststrm.helper;

import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.openliststrm.mybatisplus.domain.OpenlistStrmPlus;
import com.ruoyi.openliststrm.mybatisplus.service.IOpenlistStrmPlusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/16 20:50
 * @Version 1.0.0
 */
@Component
public class StrmHelper {

    private static final Object LOCK = new Object();

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
        AsyncManager.me().execute(() -> {
                //加锁 简单解决并发情况插入重复数据
                synchronized (LOCK) {
                    //保存或者更新
                    OpenlistStrmPlus strm = new OpenlistStrmPlus();
                    strm.setStrmPath(strmPath);
                    strm.setStrmFileName(strmFileName);
                    strm.setStrmStatus(status);
                    //存在就更新 不存在就新增
                    List<OpenlistStrmPlus> openlistStrmList = openlistStrmPlusService.lambdaQuery()
                            .eq(OpenlistStrmPlus::getStrmPath, strmPath)
                            .eq(OpenlistStrmPlus::getStrmFileName,strmFileName)
                            .list();
                    if (!CollectionUtils.isEmpty(openlistStrmList)) {
                        strm = openlistStrmList.get(0);
                        strm.setStrmStatus(status);
                        openlistStrmPlusService.updateById(strm);
                    } else {
                        strm.setStrmStatus(status);
                        openlistStrmPlusService.save(strm);
                    }
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
