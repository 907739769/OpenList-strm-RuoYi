package com.ruoyi.openliststrm.helper;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.service.ISysDictDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author Jack
 * @Date 2025/7/16 22:02
 * @Version 1.0.0
 */
@Component
public class SysDictDataHelper {

    @Autowired
    private ISysDictDataService dictDataService;

    /**
     * 查询数据字典的全部数据
     *
     * @param dictType
     * @return
     */
    public List<String> getAllValue(String dictType) {
        SysDictData data = new SysDictData();
        data.setDictType(dictType);
        List<SysDictData> dictDataList = dictDataService.selectDictDataList(data);
        return dictDataList.stream().map(SysDictData::getDictValue).collect(Collectors.toList());
    }


}
