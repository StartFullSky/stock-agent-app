package com.stockagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 决策信号实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("decision_signal")
public class DecisionSignal {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String stockCode;

    private String stockName;

    private String action;

    private Double confidence;

    private Double score;

    private Double entryPrice;

    private Double entryRangeHigh;

    private Double entryRangeLow;

    private Double stopLoss;

    private Double targetPrice;

    private String timeWindow;

    private String status;

    private String sourceAnalysis;

    private String guardrailReason;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
