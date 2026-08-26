package com.stockagent.enums;

/**
 * 股票预警类型枚举
 */
public enum AlertType {

    PRICE_CROSS_ABOVE("价格上穿"),
    PRICE_CROSS_BELOW("价格下穿"),
    PRICE_CHANGE_PCT("涨跌幅预警"),
    VOLUME_SPIKE("成交量异动"),
    RSI_OVERBOUGHT("RSI超买"),
    RSI_OVERSOLD("RSI超卖"),
    MACD_GOLDEN_CROSS("MACD金叉"),
    MACD_DEATH_CROSS("MACD死叉"),
    MA_GOLDEN_CROSS("均线金叉"),
    MA_DEATH_CROSS("均线死叉");

    private final String description;

    AlertType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
