package com.stockagent.enums;

import java.time.LocalTime;

/**
 * A股市场交易阶段枚举
 */
public enum MarketPhase {

    PREMARKET("盘前", LocalTime.of(9, 0), LocalTime.of(9, 30)),
    MORNING_SESSION("上午交易", LocalTime.of(9, 30), LocalTime.of(11, 30)),
    LUNCH_BREAK("午间休市", LocalTime.of(11, 30), LocalTime.of(13, 0)),
    AFTERNOON_SESSION("下午交易", LocalTime.of(13, 0), LocalTime.of(14, 57)),
    CLOSING_AUCTION("收盘集合竞价", LocalTime.of(14, 57), LocalTime.of(15, 0)),
    POSTMARKET("盘后", LocalTime.of(15, 0), LocalTime.of(18, 0));

    private final String label;
    private final LocalTime openTime;
    private final LocalTime closeTime;

    MarketPhase(String label, LocalTime openTime, LocalTime closeTime) {
        this.label = label;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public String getLabel() {
        return label;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    /**
     * 根据当前时间判断当前所处的市场阶段
     *
     * @return 当前市场阶段
     */
    public static MarketPhase fromNow() {
        LocalTime now = LocalTime.now();
        for (MarketPhase phase : values()) {
            if (now.isBefore(phase.closeTime) && !now.isBefore(phase.openTime)) {
                return phase;
            }
        }
        // 如果当前时间在盘后之后，返回POSTMARKET
        return POSTMARKET;
    }
}
