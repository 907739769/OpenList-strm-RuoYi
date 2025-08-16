package com.ruoyi.openliststrm.rename.extractor.impl;

import com.ruoyi.openliststrm.rename.extractor.Extractor;
import com.ruoyi.openliststrm.rename.model.MediaInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Jack
 * @Date 2025/8/12 16:52
 * @Version 1.0.0
 */
public class YearSeasonEpisodeExtractor implements Extractor {
    private static final Pattern YEAR = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
    private static final Pattern S_E = Pattern.compile("\\bS(\\d{1,2})E(\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern Sonly = Pattern.compile("\\bS(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern Eonly = Pattern.compile("\\bE(\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String extract(String name, MediaInfo info) {
        Matcher y = YEAR.matcher(name);
        int iy = Integer.MAX_VALUE;
        if (y.find()) {
            iy = y.start();
            info.setYear(y.group(1));
        }
        // SxxEyy
        Matcher se = S_E.matcher(name);
        int ise = Integer.MAX_VALUE;
        if (se.find()) {
            ise = se.start();
            info.setSeason(String.format("%02d", Integer.parseInt(se.group(1))));
            info.setEpisode(String.format("%02d", Integer.parseInt(se.group(2))));
            name = name.substring(0, Math.min(ise, iy));
            return name.trim();
        }

        Matcher so = Sonly.matcher(name);
        int iso = Integer.MAX_VALUE;
        if (so.find() && info.getSeason() == null) {
            iso = so.start();
            info.setSeason(String.format("%02d", Integer.parseInt(so.group(1))));
        }
        Matcher eo = Eonly.matcher(name);
        int ieo = Integer.MAX_VALUE;
        if (eo.find() && info.getEpisode() == null) {
            ieo = eo.start();
            info.setEpisode(String.format("%02d", Integer.parseInt(eo.group(1))));
        }
        if (iy == Integer.MAX_VALUE && iso == Integer.MAX_VALUE && ieo == Integer.MAX_VALUE) {
            return name.trim();
        }
        name = name.substring(0, Math.min(Math.min(iy, iso), ieo));
        return name.trim();
    }
}