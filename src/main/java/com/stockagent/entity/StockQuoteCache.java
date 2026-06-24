package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 股票行情缓存实体
 * 注意：当前StockService使用Redis进行行情缓存，此实体作为数据库级缓存备用方案保留。
 * 如需启用数据库缓存，可在StockService中注入StockQuoteCacheMapper使用。
 */
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
