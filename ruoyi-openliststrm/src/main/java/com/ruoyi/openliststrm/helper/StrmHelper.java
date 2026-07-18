package com.ruoyi.openliststrm.helper;

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

    /**
     * 新增或更新 strm 记录。
     * <p>
     * 表上有唯一索引 {@code idx_path_name(strm_path, strm_file_name)}，因此采用"先插入、
     * 冲突再更新"策略：绝大多数调用是新增（一次写入即可），仅在记录已存在时才回退到按唯一键更新。
     * 相比原先"先 getOne 查询再决定 insert/update"少一次查询，也消除了查询与写入之间的竞态窗口。
     */
    public void addStrm(String strmPath, String strmFileName, String status) {
        AsyncManager.me().execute(() -> {
            OpenlistStrmPlus strm = new OpenlistStrmPlus();
            strm.setStrmPath(strmPath);
            strm.setStrmFileName(strmFileName);
            strm.setStrmStatus(status);
            try {
                openlistStrmPlusService.save(strm);
            } catch (Exception e) {
                if (isDuplicateKey(e)) {
                    // 已存在则按唯一键更新状态
                    openlistStrmPlusService.lambdaUpdate()
                            .eq(OpenlistStrmPlus::getStrmPath, strmPath)
                            .eq(OpenlistStrmPlus::getStrmFileName, strmFileName)
                            .set(OpenlistStrmPlus::getStrmStatus, status)
                            .update();
                } else {
                    log.error("Error adding strm: path={}, fileName={}", strmPath, strmFileName, e);
                }
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
                .exists();
    }

    /** 判断异常链中是否为唯一键冲突（Duplicate entry） */
    static boolean isDuplicateKey(Throwable e) {
        if (e instanceof org.springframework.dao.DuplicateKeyException) {
            return true;
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("Duplicate entry")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}
