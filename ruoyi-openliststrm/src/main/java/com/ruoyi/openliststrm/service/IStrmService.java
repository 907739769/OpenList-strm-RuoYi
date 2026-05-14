package com.ruoyi.openliststrm.service;

import java.util.List;

public interface IStrmService {

    void strmDir(String path);

    void strmOneFile(String path);

    /**
     * 批量删除网盘文件并更新记录
     */
    void batchRemoveNetDisk(List<String> idList);

    /**
     * 重试STRM任务
     */
    void retryStrm(List<String> idList);

}
