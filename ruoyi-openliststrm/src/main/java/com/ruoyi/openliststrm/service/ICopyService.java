package com.ruoyi.openliststrm.service;

/**
 * @Author Jack
 * @Date 2025/7/17 18:50
 * @Version 1.0.0
 */
public interface ICopyService {

    //同步目录所有文件
    void syncFiles(String srcDir, String dstDir);

    //同步一个文件
    void syncOneFile(String srcDir, String dstDir, String relativePath);

    //同步指定子目录
    void syncFiles(String srcDir, String dstDir, String relativePath);

}
