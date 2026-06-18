package com.stockagent.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class BacktestResultDTO {
    private String strategyName;
    private String stockCode;
    private String period;
    private BigDecimal initialCapital;
    private BigDecimal finalCapital;
    private BigDecimal totalReturn;
    private BigDecimal annualReturn;
    private BigDecimal maxDrawdown;
    private Integer tradeCount;
    private Integer winCount;
    private BigDecimal winRate;
    private List<Map<String, Object>> dailyNav;
    private String description;
}
