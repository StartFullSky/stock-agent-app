package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_trade")
public class PortfolioTrade {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long portfolioId;
    private String tradeType;
    private BigDecimal price;
    private Integer quantity;
    private LocalDate tradeDate;
    private BigDecimal fee;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
