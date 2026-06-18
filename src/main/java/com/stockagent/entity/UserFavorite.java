package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_favorite")
public class UserFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String stockCode;
    private String stockName;
    private String market;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
