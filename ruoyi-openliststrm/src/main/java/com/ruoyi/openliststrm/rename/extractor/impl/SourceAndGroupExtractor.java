package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提取来源 (Source)、制作组 (Group) 和 媒体标签 (Tags)
 * 并清理文件名末尾的残留签名
 */
public class SourceAndGroupExtractor implements Extractor {

    // 1. 标签正则：增加 HDR10+, DV (Dolby Vision), IMAX, 3D 等
    private static final Pattern TAGS = Pattern.compile("\\b(REMUX|ISO|ENCODED|PROPER|REPACK|Atmos|HDR10\\+|HDR10|HDR|10bit|12bit|60fps|DV|DoVi|IMAX|3D)\\b", Pattern.CASE_INSENSITIVE);

    // 2. 来源正则：【关键修改】Blu-?Ray 支持 Blu-ray 和 BluRay
    private static final Pattern SOURCE = Pattern.compile("\\b(WEB-?DL|Blu-?Ray|BRRip|HDRip|HDTV|BDRip|CAM|WEB|DVD|DVDRip)\\b", Pattern.CASE_INSENSITIVE);

    // 3. 制作组正则：支持以 - 或 @ 开头的组名，或者 [Group] 开头
    // (?:[-@]|\\s@) 允许前面有空格的 @，处理被 normalize 替换后的字符
    private static final Pattern GROUP_END = Pattern.compile("(?:[-@]|\\s@)\\s*([A-Za-z0-9_\\.-]+)$");
    private static final Pattern GROUP_BRACKET = Pattern.compile("^\\[([A-Za-z0-9_\\.-]+)\\]");

    @Override
    public String extract(String name, MediaInfo info) {
        // --- 1. 提取并移除标签 (Tags) ---
        Matcher t = TAGS.matcher(name);
        StringBuilder cleanName = new StringBuilder(name);
        while (t.find()) {
            String tag = t.group(1).toUpperCase();
            // 统一标准化标签名称
            if (tag.equals("DV") || tag.equals("DOVI")) tag = "Dolby Vision";
            info.getTags().add(tag);
            // 将匹配到的标签替换为空格（不改变字符串长度，防止索引错位）
            for (int i = t.start(); i < t.end(); i++) cleanName.setCharAt(i, ' ');
        }
        // 压缩多余空格
        name = cleanName.toString().replaceAll("\\s+", " ").trim();

        // --- 2. 提取并移除来源 (Source) ---
        Matcher s = SOURCE.matcher(name);
        if (s.find() && info.getSource() == null) {
            String rawSource = s.group(1).toUpperCase();
            // 标准化 Source 名称
            String normalizedSource = rawSource.replaceAll("[-\\.]", "");
            if (normalizedSource.equals("BLURAY")) normalizedSource = "BluRay"; // 保持驼峰习惯

            info.setSource(normalizedSource);
            // 移除来源字符串
            name = (name.substring(0, s.start()) + " " + name.substring(s.end())).trim();
        }

        // --- 3. 提取并移除制作组 (Group) ---
        // 优先检查开头 [Group]
        Matcher gb = GROUP_BRACKET.matcher(name);
        if (gb.find()) {
            String candidate = gb.group(1);
            // 排除常见误判
            if (!candidate.matches("(?i)4k|1080p|web-dl")) {
                info.setReleaseGroup(candidate);
                name = name.substring(gb.end()).trim();
            }
        } else {
            // 检查结尾 -Group 或 @Group
            Matcher ge = GROUP_END.matcher(name);
            if (ge.find()) {
                info.setReleaseGroup(ge.group(1));
                // 截断到组名之前
                name = name.substring(0, ge.start()).trim();
            }
        }

        // --- 4. 【关键】清理末尾残留的短字符串 (如 cXcY) ---
        // 很多时候 Source 和 Group 被提取后，中间夹着的压制者签名会残留在末尾
        boolean changed = true;
        while (changed) {
            changed = false;
            String oldName = name;

            // 移除末尾的 2-6 位字符（字母数字组合）
            // (?!(?:19|20)\d{2}$) -> 负向先行断言，保护 19xx 或 20xx 这种年份不被删除
            name = name.replaceAll("\\s+(?!(?:19|20)\\d{2}$)[a-zA-Z0-9]{2,6}$", "");

            if (!name.equals(oldName)) {
                changed = true;
            }
        }

        return name.trim();
    }
}