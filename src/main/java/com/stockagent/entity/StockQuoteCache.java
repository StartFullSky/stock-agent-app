package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("stock_quote_cache")
public class StockQuoteCache {
    @TableId(type = IdType.ASSIGN_ID)
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
}
