package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票预警实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("stock_alert")
public class StockAlert {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 股票代码 */
    private String stockCode;

    /** 股票名称 */
    private String stockName;

    /** 预警类型 */
    private String alertType;

    /** 阈值 */
    private Double threshold;

    /** 是否启用 */
    private Boolean enabled;

    /** 上次触发时间 */
    private LocalDateTime lastTriggeredAt;

    /** 冷却时间（分钟），默认60分钟 */
    @Builder.Default
    private Integer cooldownMinutes = 60;

    /** 预警消息 */
    private String message;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
