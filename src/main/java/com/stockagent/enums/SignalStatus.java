package com.stockagent.enums;

import lombok.Getter;

/**
 * 信号状态枚举
 */
@Getter
public enum SignalStatus {

    ACTIVE("活跃", "ACTIVE"),
    EXPIRED("已过期", "EXPIRED"),
    INVALIDATED("已作废", "INVALIDATED"),
    CLOSED("已关闭", "CLOSED"),
    ARCHIVED("已归档", "ARCHIVED");

    private final String chineseLabel;
    private final String englishLabel;

    SignalStatus(String chineseLabel, String englishLabel) {
        this.chineseLabel = chineseLabel;
        this.englishLabel = englishLabel;
    }
}
