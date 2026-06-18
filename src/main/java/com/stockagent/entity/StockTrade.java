package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_trade")
public class StockTrade {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String stockCode;
    private String stockName;
    private Integer tradeType;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal fee;
    private LocalDate tradeDate;
    private String remark;
    private LocalDateTime createdAt;
}
