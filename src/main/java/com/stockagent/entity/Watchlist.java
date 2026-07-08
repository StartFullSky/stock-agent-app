package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_watchlist")
public class Watchlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private String stockName;
    private String market;
    private String groupName;
    private Integer sortOrder;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
