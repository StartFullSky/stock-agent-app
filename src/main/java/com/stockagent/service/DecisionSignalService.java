package com.stockagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.entity.DecisionSignal;
import com.stockagent.enums.DecisionAction;
import com.stockagent.enums.SignalStatus;
import com.stockagent.mapper.DecisionSignalMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionSignalService {

    private final DecisionSignalMapper decisionSignalMapper;

    /**
     * 创建决策信号，自动作废冲突的旧信号
     * BUY/ADD 与 SELL/REDUCE 互为冲突
     */
    @Transactional
    public DecisionSignal createSignal(DecisionSignal signal) {
        // 设置默认值
        if (signal.getStatus() == null) {
            signal.setStatus(SignalStatus.ACTIVE.name());
        }
        if (signal.getCreatedAt() == null) {
            signal.setCreatedAt(LocalDateTime.now());
        }
        signal.setUpdatedAt(LocalDateTime.now());

        // 如果没有设置过期时间，默认30天后
        if (signal.getExpiresAt() == null) {
            signal.setExpiresAt(LocalDateTime.now().plusDays(30));
        }

        // 作废冲突信号：BUY/ADD 与 SELL/REDUCE 互为冲突
        invalidateConflictingSignals(signal.getStockCode(), signal.getAction());

        decisionSignalMapper.insert(signal);
        log.info("创建决策信号: id={}, stock={}, action={}, status={}",
                signal.getId(), signal.getStockCode(), signal.getAction(), signal.getStatus());
        return signal;
    }

    /**
     * 作废冲突信号：买入方向与卖出方向互斥
     */
    private void invalidateConflictingSignals(String stockCode, String newAction) {
        DecisionAction action = DecisionAction.valueOf(newAction);

        List<String> conflictingActions = getConflictingActions(action);
        if (conflictingActions.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DecisionSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecisionSignal::getStockCode, stockCode)
                .eq(DecisionSignal::getStatus, SignalStatus.ACTIVE.name())
                .in(DecisionSignal::getAction, conflictingActions);

        List<DecisionSignal> conflictingSignals = decisionSignalMapper.selectList(wrapper);
        for (DecisionSignal oldSignal : conflictingSignals) {
            oldSignal.setStatus(SignalStatus.INVALIDATED.name());
            oldSignal.setGuardrailReason("被新信号作废: " + action.getChineseLabel());
            oldSignal.setUpdatedAt(LocalDateTime.now());
            decisionSignalMapper.updateById(oldSignal);
            log.info("作废冲突信号: id={}, stock={}, oldAction={}, reason={}",
                    oldSignal.getId(), stockCode, oldSignal.getAction(), oldSignal.getGuardrailReason());
        }
    }

    /**
     * 获取与给定动作冲突的动作列表
     */
    private List<String> getConflictingActions(DecisionAction action) {
        return switch (action) {
            case BUY, ADD -> List.of(DecisionAction.SELL.name(), DecisionAction.REDUCE.name());
            case SELL, REDUCE -> List.of(DecisionAction.BUY.name(), DecisionAction.ADD.name());
            default -> List.of();
        };
    }

    /**
     * 获取某股票的活跃信号列表
     */
    public List<DecisionSignal> getActiveSignals(String stockCode) {
        LambdaQueryWrapper<DecisionSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecisionSignal::getStockCode, stockCode)
                .eq(DecisionSignal::getStatus, SignalStatus.ACTIVE.name())
                .orderByDesc(DecisionSignal::getCreatedAt);
        return decisionSignalMapper.selectList(wrapper);
    }

    /**
     * 评估信号是否正确
     * 根据未来价格与入场价/止损价/目标价对比判断
     */
    @Transactional
    public DecisionSignal evaluateSignal(String signalId, double futurePrice) {
        DecisionSignal signal = decisionSignalMapper.selectById(signalId);
        if (signal == null) {
            throw new RuntimeException("信号不存在: " + signalId);
        }
        if (!SignalStatus.ACTIVE.name().equals(signal.getStatus())) {
            throw new RuntimeException("信号状态非活跃，无法评估: " + signal.getStatus());
        }

        DecisionAction action = DecisionAction.valueOf(signal.getAction());
        boolean isCorrect = false;

        switch (action) {
            case BUY, ADD -> {
                // 买入方向：未来价格上涨为正确
                isCorrect = futurePrice > (signal.getEntryPrice() != null ? signal.getEntryPrice() : 0);
            }
            case SELL, REDUCE -> {
                // 卖出方向：未来价格下跌为正确
                isCorrect = futurePrice < (signal.getEntryPrice() != null ? signal.getEntryPrice() : 0);
            }
            default -> {
                // WATCH/HOLD/AVOID/ALERT 用中性评估
                if (signal.getStopLoss() != null && futurePrice < signal.getStopLoss()) {
                    isCorrect = false;
                } else if (signal.getTargetPrice() != null && futurePrice >= signal.getTargetPrice()) {
                    isCorrect = true;
                } else {
                    // 无明确止损/目标时，保持信号不关闭
                    return signal;
                }
            }
        }

        // 标记为已关闭
        signal.setStatus(SignalStatus.CLOSED.name());
        signal.setUpdatedAt(LocalDateTime.now());
        if (isCorrect) {
            signal.setGuardrailReason("评估正确: 价格 " + futurePrice);
        } else {
            signal.setGuardrailReason("评估错误: 价格 " + futurePrice);
        }
        decisionSignalMapper.updateById(signal);

        log.info("评估信号: id={}, action={}, futurePrice={}, correct={}",
                signalId, action, futurePrice, isCorrect);
        return signal;
    }

    /**
     * 获取某股票的信号统计信息
     * @return Map 包含: total, accuracy, byAction
     */
    public Map<String, Object> getSignalStats(String stockCode) {
        Map<String, Object> stats = new HashMap<>();

        LambdaQueryWrapper<DecisionSignal> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DecisionSignal::getStockCode, stockCode)
                .eq(DecisionSignal::getStatus, SignalStatus.CLOSED.name());
        List<DecisionSignal> closedSignals = decisionSignalMapper.selectList(wrapper);

        stats.put("stockCode", stockCode);
        stats.put("total", closedSignals.size());

        // 计算准确率（根据guardrailReason中的"正确"/"错误"判断）
        long correctCount = closedSignals.stream()
                .filter(s -> s.getGuardrailReason() != null && s.getGuardrailReason().contains("正确"))
                .count();
        double accuracy = closedSignals.isEmpty() ? 0.0 : (double) correctCount / closedSignals.size() * 100;
        stats.put("accuracy", Math.round(accuracy * 100.0) / 100.0);
        stats.put("correctCount", correctCount);

        // 按动作分类统计
        Map<String, Long> byAction = closedSignals.stream()
                .collect(Collectors.groupingBy(DecisionSignal::getAction, Collectors.counting()));
        stats.put("byAction", byAction);

        // 活跃信号数量
        LambdaQueryWrapper<DecisionSignal> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(DecisionSignal::getStockCode, stockCode)
                .eq(DecisionSignal::getStatus, SignalStatus.ACTIVE.name());
        long activeCount = decisionSignalMapper.selectCount(activeWrapper);
        stats.put("activeCount", activeCount);

        return stats;
    }
}
