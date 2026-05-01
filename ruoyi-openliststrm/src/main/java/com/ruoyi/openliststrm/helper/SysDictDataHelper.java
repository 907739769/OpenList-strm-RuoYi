package com.ruoyi.openliststrm.helper;

import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.system.service.ISysDictDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SysDictDataHelper {

    @Autowired
    private ISysDictDataService dictDataService;

    private final ConcurrentHashMap<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> getAllValue(String dictType) {
        if (dictType == null) {
            return Collections.emptyList();
        }
        return cache.computeIfAbsent(dictType, this::loadDictValues);
    }

    public void refreshCache(String dictType) {
        if (dictType == null) return;
        cache.remove(dictType);
        log.debug("Refreshed cache for dictType: {}", dictType);
    }

    public void refreshAllCache() {
        cache.clear();
        log.debug("Cleared all dict cache");
    }

    private List<String> loadDictValues(String dictType) {
        try {
            SysDictData data = new SysDictData();
            data.setDictType(dictType);
            List<SysDictData> dictDataList = dictDataService.selectDictDataList(data);
            List<String> values = dictDataList.stream()
                    .map(SysDictData::getDictValue)
                    .collect(Collectors.toList());
            log.debug("Loaded dictType={}, {} values", dictType, values.size());
            return values;
        } catch (Exception e) {
            log.error("Failed to load dictType: {}", dictType, e);
            return Collections.emptyList();
        }
    }

    @PreDestroy
    public void destroy() {
        cache.clear();
    }
}
