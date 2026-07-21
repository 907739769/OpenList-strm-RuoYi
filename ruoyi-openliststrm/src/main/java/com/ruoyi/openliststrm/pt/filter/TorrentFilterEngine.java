package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 种子过滤与择优引擎。纯逻辑，不读数据库、不发网络请求——生效条件由调用方
 * 通过 {@link FilterCriteria} 传入（见 {@link FilterCriteriaFactory}）。
 *
 * @author Jack
 */
@Slf4j
@Component
public class TorrentFilterEngine {

    /**
     * 硬性过滤：淘汰不满足条件的候选，保留原顺序。
     * <p>
     * 被淘汰的种子不落库，只记 debug 日志并带上具体原因（哪条规则、阈值、实际值）——
     * 这些日志是后续调优过滤规则的主要素材。
     * </p>
     *
     * @return 新的可变列表，调用方修改它不会影响入参
     */
    public List<TorrentInfo> filter(List<TorrentInfo> candidates, FilterCriteria criteria) {
        List<TorrentInfo> survivors = new ArrayList<>();
        for (TorrentInfo torrent : candidates) {
            String reason = rejectReason(torrent, criteria);
            if (reason == null) {
                survivors.add(torrent);
            } else {
                log.debug("种子被过滤：{} —— {}", torrent.getTitle(), reason);
            }
        }
        return survivors;
    }

    /**
     * 返回淘汰原因；返回 null 表示通过。
     */
    private String rejectReason(TorrentInfo torrent, FilterCriteria criteria) {
        if (torrent.getSeeders() < criteria.minSeeders()) {
            return "做种数 " + torrent.getSeeders() + " 低于下限 " + criteria.minSeeders();
        }
        if (criteria.minSize() > 0 && torrent.getSize() < criteria.minSize()) {
            return "体积 " + torrent.getSize() + " 小于下限 " + criteria.minSize();
        }
        if (criteria.maxSize() > 0 && torrent.getSize() > criteria.maxSize()) {
            return "体积 " + torrent.getSize() + " 超过上限 " + criteria.maxSize();
        }
        if (criteria.freeOnly() && !torrent.isFree()) {
            return "非免费种(下载量系数 " + torrent.getDownloadVolumeFactor() + ")，而配置为仅要免费";
        }

        String title = torrent.getTitle();
        // 标题缺失的条目无法做关键词判定，一律淘汰而非放行
        if (StringUtils.isBlank(title)) {
            return "标题为空，无法判定";
        }
        String lower = title.toLowerCase(Locale.ROOT);

        for (String keyword : criteria.excludeKeywords()) {
            if (lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return "命中排除词「" + keyword + "」";
            }
        }
        if (!criteria.includeKeywords().isEmpty() && !containsAny(lower, criteria.includeKeywords())) {
            return "未命中任何包含词 " + criteria.includeKeywords();
        }
        return null;
    }

    private boolean containsAny(String lowerTitle, List<String> keywords) {
        for (String keyword : keywords) {
            if (lowerTitle.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
