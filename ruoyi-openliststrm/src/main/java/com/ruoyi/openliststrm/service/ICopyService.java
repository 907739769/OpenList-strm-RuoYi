package com.ruoyi.openliststrm.service;

import java.util.List;

public interface ICopyService {

    //同步目录所有文件
    void syncFiles(String srcDir, String dstDir);

    //同步一个文件
    void syncOneFile(String srcDir, String dstDir, String relativePath);

    //同步指定子目录
    void syncFiles(String srcDir, String dstDir, String relativePath);

    /**
     * 批量删除网盘文件并更新记录
     */
    void batchRemoveNetDisk(List<String> idList);

    /**
     * 重试复制任务
     */
    void retryCopy(List<String> idList);

}
