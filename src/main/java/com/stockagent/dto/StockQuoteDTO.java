package com.stockagent.dto;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class StockQuoteDTO {
    private String stockCode;
    private String stockName;
    private BigDecimal currentPrice;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal preClose;
    private BigDecimal changeAmount;
    private BigDecimal changeRate;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal turnoverRate;
    private BigDecimal peRatio;
    private BigDecimal totalMarketCap;
    private BigDecimal circulatingMarketCap;
}
