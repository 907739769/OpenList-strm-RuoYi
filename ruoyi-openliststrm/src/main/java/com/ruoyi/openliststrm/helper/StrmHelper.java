package com.ruoyi.openliststrm.helper;

import com.ruoyi.openliststrm.domain.OpenlistStrm;
import com.ruoyi.openliststrm.service.IOpenlistStrmService;
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

    @Autowired
    private IOpenlistStrmService strmService;

    /**
     * 添加strm
     *
     * @param strmPath
     * @param strmFileName
     * @param status
     */
    public void addStrm(String strmPath, String strmFileName, String status) {
        OpenlistStrm strm = new OpenlistStrm();
        strm.setStrmPath(strmPath);
        strm.setStrmFileName(strmFileName);
        //存在就更新 不存在就新增
        List<OpenlistStrm> openlistStrmList = strmService.selectOpenlistStrmList(strm);
        if (!CollectionUtils.isEmpty(openlistStrmList)) {
            strm = openlistStrmList.get(0);
            strm.setStrmStatus(status);
            strmService.updateOpenlistStrm(strm);
        } else {
            strm.setStrmStatus(status);
            strmService.insertOpenlistStrm(strm);
        }
    }

    /**
     * 判断strm的文件是否处理过
     *
     * @param strmPath
     * @param strmFileName
     * @return
     */
    public boolean exitStrm(String strmPath, String strmFileName) {
        OpenlistStrm strm = new OpenlistStrm();
        strm.setStrmPath(strmPath);
        strm.setStrmFileName(strmFileName);
        strm.setStrmStatus("1");
        return !CollectionUtils.isEmpty(strmService.selectOpenlistStrmList(strm));
    }


}
