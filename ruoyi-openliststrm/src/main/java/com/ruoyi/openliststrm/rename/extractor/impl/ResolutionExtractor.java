package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Jack
 * @Date 2025/8/12 16:50
 * @Version 1.0.0
 */
public class ResolutionExtractor implements Extractor {
    private static final Pattern P = Pattern.compile("\\b(4k|2160p|1080p|1080i|720p|480p)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String extract(String name, MediaInfo info) {
        Matcher m = P.matcher(name);
        if (m.find()) {
            info.setResolution(normalizeResolution(m.group(1)));
            name = (name.substring(0, m.start()) + name.substring(m.end())).trim();
        }
        return name;
    }

    private String normalizeResolution(String raw) {
        String r = raw.toLowerCase();
        if (r.equals("4k")) return "2160p";
        return r;
    }
}