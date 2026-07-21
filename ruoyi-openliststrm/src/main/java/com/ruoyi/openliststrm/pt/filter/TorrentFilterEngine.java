package com.ruoyi.openliststrm.pt.filter;

import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
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
     * 从候选中挑出最优的一个。
     * <p>
     * 按 {@link FilterCriteria#sortPriority()} 的维度顺序，把各维度的比较器用
     * thenComparing 串联后取排在最前的那个。维度顺序由配置决定，因此同一批候选
     * 在不同配置下会选出不同的赢家——这正是「排序权重可调」的实现方式。
     * </p>
     * <p>
     * 全部维度都判同级时返回列表中的第一个（比较过程不改变入参列表的顺序）。
     * </p>
     *
     * @return 最优候选；候选为空时返回 null
     */
    public TorrentInfo pickBest(List<TorrentInfo> candidates, FilterCriteria criteria) {
        if (candidates.isEmpty()) {
            return null;
        }
        Comparator<TorrentInfo> comparator = null;
        for (SortDimension dimension : criteria.sortPriority()) {
            Comparator<TorrentInfo> next = dimension.comparator(criteria);
            comparator = (comparator == null) ? next : comparator.thenComparing(next);
        }
        if (comparator == null) {
            // FilterCriteria 保证 sortPriority 非空，这里只是防御
            return candidates.get(0);
        }

        TorrentInfo best = candidates.get(0);
        for (int i = 1; i < candidates.size(); i++) {
            // 严格小于才替换，保证同级时保留先出现的那个
            if (comparator.compare(candidates.get(i), best) < 0) {
                best = candidates.get(i);
            }
        }
        log.debug("择优结果：{}（候选 {} 个，维度顺序 {}）",
                best.getTitle(), candidates.size(), criteria.sortPriority());
        return best;
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
