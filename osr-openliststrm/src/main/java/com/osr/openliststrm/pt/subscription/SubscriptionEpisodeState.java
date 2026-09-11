package com.osr.openliststrm.pt.subscription;

/**
 * pt_subscription_episode.state 取值的唯一权威定义。之前 {@code SubscriptionService}、
 * {@code SubscriptionEngine}、{@code AutoSearchService} 里各自维护一份同值的私有字符串常量，
 * 拼写漂移不会被编译期发现，统一到这里后其余类只引用 {@link #value()}。
 *
 * @author Jack
 */
public enum SubscriptionEpisodeState {

    /** 尚未匹配到资源 */
    MISSING("MISSING"),
    /** 已占位并推送给下载器，等待完成 */
    IN_FLIGHT("IN_FLIGHT"),
    /** 已在媒体服务器确认入库 */
    IN_LIBRARY("IN_LIBRARY"),
    /**
     * 已入库，且正在下载一个质量更好的版本（洗版）。
     * <p>
     * 刻意不复用 {@link #IN_FLIGHT}：那会让进度显示把这一集算成"未入库"（旧文件明明还在库里），
     * 更要命的是洗版下载失败时 {@code DownloadTrackService#fail} 会把 IN_FLIGHT 退回 MISSING，
     * 于是一次洗版失败让这一集显示成缺失、被 RSS 从头重下一遍，比不洗版还糟。
     * UPGRADING 失败时退回的是 IN_LIBRARY。
     * </p>
     */
    UPGRADING("UPGRADING"),
    /** 连续失败达到熔断阈值，不再被 RSS/补搜自动捞回，需人工在下载记录管理页重试 */
    BLOCKED("BLOCKED");

    private final String value;

    SubscriptionEpisodeState(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 面向用户的中文名，用于日志与匹配日志的文案。
     * <p>
     * 与前端进度弹窗的用词保持一致——同一个状态在页面上叫「在途」、在匹配日志里叫别的说法，
     * 用户会以为是两回事。
     * </p>
     */
    public String label() {
        return switch (this) {
            case MISSING -> "缺失";
            case IN_FLIGHT -> "在途";
            case IN_LIBRARY -> "已入库";
            case UPGRADING -> "洗版中";
            case BLOCKED -> "已熔断";
        };
    }

    /**
     * 按库里存的字符串反查枚举，认不出返回 {@code null}。
     * <p>
     * 认不出时返回 null 而不是抛异常：调用方都是日志/文案场景，为一个没见过的状态值
     * 中断主流程说不过去，交给调用方退回原样输出即可。
     * </p>
     */
    public static SubscriptionEpisodeState from(String value) {
        if (value == null) {
            return null;
        }
        for (SubscriptionEpisodeState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        return null;
    }

    /** 库里存的状态值对应的中文名，认不出时原样返回（好过显示一个空白） */
    public static String labelOf(String value) {
        SubscriptionEpisodeState state = from(value);
        return state == null ? String.valueOf(value) : state.label();
    }
}
