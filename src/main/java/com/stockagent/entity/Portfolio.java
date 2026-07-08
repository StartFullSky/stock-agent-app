package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_portfolio")
public class Portfolio {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private String stockName;
    private String market;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime closeTime;
}
