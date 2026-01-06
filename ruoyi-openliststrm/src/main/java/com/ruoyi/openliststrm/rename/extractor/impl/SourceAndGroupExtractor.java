package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Jack
 * @Date 2025/8/12 16:51
 * @Version 1.0.0
 */
public class SourceAndGroupExtractor implements Extractor {
    private static final Pattern SOURCE = Pattern.compile("\\b(WEB-?DL|BluRay|BRRip|HDRip|HDTV|BDRip|CAM|WEB)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP = Pattern.compile("[-@]([A-Za-z0-9]+)$");
    private static final Pattern TAGS = Pattern.compile("\\b(REMUX|ENCODED|PROPER|REPACK|Atmos|HDR|10bit|60fps)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String extract(String name, MediaInfo info) {
        Matcher s = SOURCE.matcher(name);
        if (s.find() && info.getSource() == null) {
            info.setSource(s.group(1).replaceAll("\\.", "").toUpperCase());
            name = name.substring(0, s.start()) + name.substring(s.end());
        }

        Matcher t = TAGS.matcher(name);
        while (t.find()) {
            info.getTags().add(t.group(1).toUpperCase());
            name = name.substring(0, t.start()) + name.substring(t.end());
            t = TAGS.matcher(name);
        }

        Matcher g = GROUP.matcher(name);
        if (g.find()) {
            info.setReleaseGroup(g.group(1).toUpperCase());
            name = name.substring(0, g.start()).trim();
        }
        return name.trim();
    }
}