package com.stockagent.enums;

import lombok.Getter;

/**
 * 交易决策动作枚举
 * numericScore: 100=强烈买入 → 0=强烈卖出
 */
@Getter
public enum DecisionAction {

    BUY("买入", "BUY", 85),
    ADD("加仓", "ADD", 70),
    HOLD("持有", "HOLD", 50),
    REDUCE("减仓", "REDUCE", 30),
    SELL("卖出", "SELL", 15),
    WATCH("观望", "WATCH", 50),
    AVOID("回避", "AVOID", 10),
    ALERT("警报", "ALERT", 50);

    private final String chineseLabel;
    private final String englishLabel;
    private final int numericScore;

    DecisionAction(String chineseLabel, String englishLabel, int numericScore) {
        this.chineseLabel = chineseLabel;
        this.englishLabel = englishLabel;
        this.numericScore = numericScore;
    }

    /**
     * 从自然语言文本解析决策动作（支持中英文）
     */
    public static DecisionAction fromNaturalLanguage(String text) {
        if (text == null || text.isBlank()) {
            return HOLD;
        }
        String lower = text.toLowerCase().trim();

        // 英文关键词匹配
        if (lower.contains("buy") || lower.contains("long") || lower.contains("买入") || lower.contains("建仓")) {
            return BUY;
        }
        if (lower.contains("add") || lower.contains("加仓") || lower.contains("增仓") || lower.contains("加码")) {
            return ADD;
        }
        if (lower.contains("sell") || lower.contains("short") || lower.contains("卖出") || lower.contains("清仓")) {
            return SELL;
        }
        if (lower.contains("reduce") || lower.contains("减仓") || lower.contains("减持") || lower.contains("减码")) {
            return REDUCE;
        }
        if (lower.contains("hold") || lower.contains("持有") || lower.contains("维持")) {
            return HOLD;
        }
        if (lower.contains("watch") || lower.contains("观望") || lower.contains("观察")) {
            return WATCH;
        }
        if (lower.contains("avoid") || lower.contains("回避") || lower.contains("规避")) {
            return AVOID;
        }
        if (lower.contains("alert") || lower.contains("警报") || lower.contains("警告") || lower.contains("风险")) {
            return ALERT;
        }

        return HOLD;
    }

    /**
     * 根据数值分数返回对应决策动作
     * @param score 0-100 的分数
     */
    public static DecisionAction fromScore(double score) {
        if (score >= 80) return BUY;
        if (score >= 65) return ADD;
        if (score >= 55) return HOLD;
        if (score >= 45) return WATCH;
        if (score >= 30) return REDUCE;
        if (score >= 15) return SELL;
        return AVOID;
    }
}
