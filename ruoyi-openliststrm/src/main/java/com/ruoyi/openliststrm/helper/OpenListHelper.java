package com.ruoyi.openliststrm.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author Jack
 * @Date 2025/7/16 20:51
 * @Version 1.0.0
 */
@Component
public class OpenListHelper {

    @Autowired
    private SysDictDataHelper sysDictDataHelper;

    /**
     * 判断文件是视频文件
     *
     * @param name
     * @return
     */
    public boolean isVideo(String name) {
        List<String> values = sysDictDataHelper.getAllValue("openlist_video_type");
        for (String value : values) {
            if (name.toLowerCase().endsWith("." + value.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是字幕文件
     *
     * @param name
     * @return
     */
    public boolean isSrt(String name) {
        List<String> values = sysDictDataHelper.getAllValue("openlist_srt_type");
        for (String value : values) {
            if (name.toLowerCase().endsWith("." + value.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是 .strm 文件（不区分大小写）
     * 如果是 .strm 文件，应直接处理，不受最小文件大小限制
     *
     * @param name 文件名或路径
     * @return true 如果以 .strm 结尾
     */
    public boolean isStrm(String name) {
        if (name == null) {
            return false;
        }
        return name.toLowerCase().endsWith(".strm");
    }

}
