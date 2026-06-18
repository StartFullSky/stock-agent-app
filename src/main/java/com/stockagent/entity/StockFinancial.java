package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("stock_financial")
public class StockFinancial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private LocalDate reportDate;
    private String reportType;
    private BigDecimal revenue;
    private BigDecimal netProfit;
    private BigDecimal peRatio;
    private BigDecimal pbRatio;
    private BigDecimal roe;
    private BigDecimal debtRatio;
    private BigDecimal grossMargin;
}
