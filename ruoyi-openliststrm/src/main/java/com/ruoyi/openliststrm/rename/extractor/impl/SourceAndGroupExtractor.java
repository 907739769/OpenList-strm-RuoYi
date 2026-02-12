package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceAndGroupExtractor implements Extractor {

    // 优化：加入常见流媒体简写 (TX, YOUKU, IQIYI, NF, AMZN) 以及 HFR 等
    private static final Pattern TAGS = Pattern.compile(
            "\\b(REMUX|ISO|ENCODED|PROPER|REPACK|Atmos|HDR10\\+|HDR10|HDR|10bit|12bit|60fps|HFR|DV|DoVi|IMAX|TX|YOUKU|IQIYI|NF|AMZN|HMAX|DSNP)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SOURCE = Pattern.compile("\\b(WEB-?DL|WEB-?Rip|Blu-?Ray|BRRip|HDRip|HDTV|BDRip|CAM|WEB|DVD|DVDRip)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GROUP_END = Pattern.compile("(?:[-@]|\\s@)\\s*([A-Za-z0-9_\\.-]+)$");
    private static final Pattern GROUP_BRACKET = Pattern.compile("^\\[([A-Za-z0-9_\\.-]+)\\]");

    @Override
    public String extract(String name, MediaInfo info) {
        // 1. Tags
        Matcher t = TAGS.matcher(name);
        StringBuilder cleanName = new StringBuilder(name);
        while (t.find()) {
            String tag = t.group(1).toUpperCase();
            if (tag.equals("DV") || tag.equals("DOVI")) tag = "Dolby Vision";

            // 记录标签
            info.getTags().add(tag);

            // 替换为空格
            for (int i = t.start(); i < t.end(); i++) cleanName.setCharAt(i, ' ');
        }
        name = cleanName.toString().replaceAll("\\s+", " ").trim();

        // 2. Source
        Matcher s = SOURCE.matcher(name);
        if (s.find() && info.getSource() == null) {
            String rawSource = s.group(1).toUpperCase();
            String normalizedSource = rawSource.replaceAll("[-\\.]", "");
            if (normalizedSource.equals("BLURAY")) normalizedSource = "BluRay";
            if (normalizedSource.equals("WEBRIP")) normalizedSource = "WEBRip";

            info.setSource(normalizedSource);
            name = (name.substring(0, s.start()) + " " + name.substring(s.end())).trim();
        }

        // 3. Group
        Matcher gb = GROUP_BRACKET.matcher(name);
        if (gb.find()) {
            String candidate = gb.group(1);
            if (!candidate.matches("(?i)4k|1080p|web-dl|web-rip|webrip")) {
                info.setReleaseGroup(candidate);
                name = name.substring(gb.end()).trim();
            }
        } else {
            Matcher ge = GROUP_END.matcher(name);
            if (ge.find()) {
                info.setReleaseGroup(ge.group(1));
                name = name.substring(0, ge.start()).trim();
            }
        }

        return name.trim();
    }
}