package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodecExtractor implements Extractor {
    // 视频：增加 VC-1, MPEG-2 等
    private static final Pattern VIDEO = Pattern.compile("\\b(x264|h\\.?264|avc|x265|h\\.?265|hevc|av1|vp9|vp8|vc-?1|mpeg-?2)\\b", Pattern.CASE_INSENSITIVE);
    // 音频：增加 TrueHD, Atmos, Opus, Vorbis, PCM, MP3, DTS-HD
    private static final Pattern AUDIO = Pattern.compile("\\b(aac|ddp|dd\\+|ac3|dts-?hd|dts-?x|dts|truehd|atmos|opus|vorbis|flac|pcm|mp3|eac3)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String extract(String name, MediaInfo info) {
        Matcher mv = VIDEO.matcher(name);
        if (mv.find() && info.getVideoCodec() == null) {
            info.setVideoCodec(normalizeVideo(mv.group(1)));
            name = name.substring(0, mv.start()) + " " + name.substring(mv.end()); // 替换为空格防止粘连
        }
        Matcher ma = AUDIO.matcher(name);
        if (ma.find() && info.getAudioCodec() == null) {
            info.setAudioCodec(normalizeAudio(ma.group(1)));
            name = name.substring(0, ma.start()) + " " + name.substring(ma.end());
        }
        return name.replaceAll("\\s+", " ").trim();
    }

    private String normalizeVideo(String raw) {
        String r = raw.toLowerCase().replace(".", "").replace(" ", "").replace("-", "");
        if (r.contains("264") || r.contains("avc")) return "H264";
        if (r.contains("265") || r.contains("hevc")) return "H265";
        return r.toUpperCase();
    }

    private String normalizeAudio(String raw) {
        String r = raw.toLowerCase().replace("-", "").replace(" ", "");
        if (r.contains("ddp") || r.contains("dd+") || r.equals("eac3")) return "EAC3";
        if (r.contains("truehd")) return "TrueHD";
        if (r.contains("atmos")) return "Atmos";
        if (r.contains("dtshd")) return "DTS-HD";
        if (r.equals("ac3") || r.equals("dd")) return "AC3";
        return r.toUpperCase();
    }
}