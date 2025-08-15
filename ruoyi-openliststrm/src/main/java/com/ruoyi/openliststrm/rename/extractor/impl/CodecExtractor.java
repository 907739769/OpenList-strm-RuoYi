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
public class CodecExtractor implements Extractor {
    private static final Pattern VIDEO = Pattern.compile("\\b(x264|h264|h\\s264|x265|h265|hevc|h\\s265|avc)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUDIO = Pattern.compile("\\b(aac2\\s0|aac|ddp5\\s1|ddp2\\s0|ddp|dd5\\s1|dd\\+?5\\s1|dts|ac3|dts5\\s1|opus|e-?ac3|flac|truehd)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String extract(String name, MediaInfo info) {
        Matcher mv = VIDEO.matcher(name);
        if (mv.find() && info.getVideoCodec() == null) {
            info.setVideoCodec(normalizeVideo(mv.group(1)));
            name = name.substring(0, mv.start()) + name.substring(mv.end());
        }
        Matcher ma = AUDIO.matcher(name);
        if (ma.find() && info.getAudioCodec() == null) {
            info.setAudioCodec(normalizeAudio(ma.group(1)));
            name = name.substring(0, ma.start()) + name.substring(ma.end());
        }
        return name.trim();
    }

    private String normalizeVideo(String raw) {
        String r = raw.toLowerCase();
        if (r.contains("264") || r.contains("avc")) return "H264";
        if (r.contains("265") || r.contains("hevc")) return "H265";
        return raw.replace(" ", "").toUpperCase();
    }

    private String normalizeAudio(String raw) {
        String r = raw.toLowerCase();
        if (r.contains("e-ac3")) return "EAC3";
        return raw.replace(" ", ".").toUpperCase();
    }
}