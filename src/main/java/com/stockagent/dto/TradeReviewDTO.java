package com.stockagent.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class TradeReviewDTO {
    private Integer totalTrades;
    private Integer buyCount;
    private Integer sellCount;
    private BigDecimal totalBuyAmount;
    private BigDecimal totalSellAmount;
    private Integer tradeDays;
    private BigDecimal avgTradesPerWeek;
    private Integer uniqueStocks;
    private BigDecimal totalProfit;
    private Integer winCount;
    private Integer lossCount;
    private BigDecimal winRate;
    private List<Map<String, Object>> stockProfits;
    private List<String> diagnoses;

    public static TradeReviewDTO empty() {
        TradeReviewDTO dto = new TradeReviewDTO();
        dto.setTotalTrades(0);
        dto.setBuyCount(0);
        dto.setSellCount(0);
        dto.setTotalBuyAmount(BigDecimal.ZERO);
        dto.setTotalSellAmount(BigDecimal.ZERO);
        dto.setUniqueStocks(0);
        dto.setTotalProfit(BigDecimal.ZERO);
        dto.setWinCount(0);
        dto.setLossCount(0);
        dto.setWinRate(BigDecimal.ZERO);
        dto.setStockProfits(List.of());
        dto.setDiagnoses(List.of("暂无交易记录"));
        return dto;
    }
}
