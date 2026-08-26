package com.stockagent.tools;

import com.stockagent.dto.BacktestResultDTO;
import com.stockagent.dto.TradeReviewDTO;
import com.stockagent.service.BacktestService;
import com.stockagent.service.TradeReviewService;
import com.stockagent.service.WebSearchService;
import com.stockagent.service.TechnicalIndicatorService;
import com.stockagent.service.DecisionSignalService;
import com.stockagent.service.AlertService;
import com.stockagent.service.TradingCalendar;
import com.stockagent.service.StockService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {
    private final WebSearchService webSearchService;
    private final TradeReviewService tradeReviewService;
    private final BacktestService backtestService;
    private final TechnicalIndicatorService technicalIndicatorService;
    private final DecisionSignalService decisionSignalService;
    private final AlertService alertService;
    private final TradingCalendar tradingCalendar;
    private final StockService stockService;

    @Tool("在互联网上搜索财经新闻、公司资讯、行业动态")
    public String searchWeb(@P("搜索关键词") String query) {
        log.info("Web搜索: {}", query);
        try {
            List<Map<String, String>> results = webSearchService.search(query, 5);
            if (results.isEmpty()) return "未找到相关内容";
            StringBuilder sb = new StringBuilder("【搜索结果】\n\n");
            for (int i = 0; i < results.size(); i++) {
                Map<String, String> r = results.get(i);
                sb.append(String.format("%d. %s\n   来源: %s\n\n", i + 1, r.get("title"), r.get("source")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Web搜索失败", e);
            return "搜索暂时不可用，请稍后重试";
        }
    }

    @Tool("分析用户的交易行为，包括胜率、频率、盈亏诊断")
    public String reviewTrades(@P("用户ID") Long userId) {
        log.info("交易复盘: {}", userId);
        try {
            TradeReviewDTO review = tradeReviewService.analyzeTradeBehavior(userId);
            if (review.getTotalTrades() == 0) return "暂无交易记录";
            StringBuilder sb = new StringBuilder("【交易复盘】\n\n");
            sb.append(String.format("总交易: %s次（买%s/卖%s）\n", review.getTotalTrades(), review.getBuyCount(), review.getSellCount()));
            sb.append(String.format("总盈亏: %s 元\n", review.getTotalProfit()));
            sb.append(String.format("胜率: %s%%\n\n", review.getWinRate()));
            if (review.getDiagnoses() != null) {
                sb.append("【诊断】\n");
                for (String d : review.getDiagnoses()) sb.append("  ").append(d).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("交易复盘失败", e);
            return "复盘分析暂时不可用，请稍后重试";
        }
    }

    @Tool("回测双均线策略，输入股票代码和均线周期")
    public String backtestMA(@P("股票代码") String stockCode, @P("短周期") String shortPeriod, @P("长周期") String longPeriod) {
        log.info("回测: {}", stockCode);
        try {
            String end = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String start = LocalDate.now().minusYears(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            int sp = shortPeriod != null ? Integer.parseInt(shortPeriod) : 5;
            int lp = longPeriod != null ? Integer.parseInt(longPeriod) : 20;
            BacktestResultDTO r = backtestService.backtestMovingAverage(stockCode, sp, lp, start, end, BigDecimal.valueOf(100000));
            return String.format("【回测结果】\n策略: %s\n收益率: %s%%\n最大回撤: %s%%\n交易次数: %s\n胜率: %s%%\n\n⚠️ 以上回测结果基于历史数据，不代表未来收益。",
                r.getStrategyName(), r.getTotalReturn(), r.getMaxDrawdown(), r.getTradeCount(), r.getWinRate());
        } catch (Exception e) {
            log.error("回测失败", e);
            return "回测暂时不可用: " + e.getMessage();
        }
    }

    @Tool("计算股票技术指标，包括MA/EMA/MACD/RSI/KDJ/BOLL等")
    public String calculateIndicators(@P("股票代码") String stockCode) {
        log.info("计算技术指标: {}", stockCode);
        try {
            List<Map<String, Object>> klines = stockService.getKlineData(stockCode, "", "", 100);
            if (klines == null || klines.isEmpty()) {
                return "未获取到" + stockCode + "的K线数据";
            }
            Map<String, Object> indicators = technicalIndicatorService.calculateAll(klines);
            StringBuilder sb = new StringBuilder("【技术指标分析 - " + stockCode + "】\n\n");
            sb.append(String.format("数据条数: %d 条\n\n", klines.size()));
            if (indicators != null) {
                for (Map.Entry<String, Object> entry : indicators.entrySet()) {
                    sb.append(String.format("【%s】: %s\n", entry.getKey(), entry.getValue()));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("技术指标计算失败: {}", stockCode, e);
            return "技术指标计算暂时不可用: " + e.getMessage();
        }
    }

    @Tool("创建交易决策信号（买入/卖出/持有等）")
    public String createDecisionSignal(@P("股票代码") String stockCode,
                                        @P("操作建议: BUY/SELL/HOLD/ADD/REDUCE/WATCH/AVOID") String action,
                                        @P("信心度0-100") String confidence,
                                        @P("目标价格") String targetPrice,
                                        @P("止损价格") String stopLoss) {
        log.info("创建决策信号: {} action={}", stockCode, action);
        try {
            com.stockagent.entity.DecisionSignal signalEntity = com.stockagent.entity.DecisionSignal.builder()
                    .stockCode(stockCode)
                    .action(action.toUpperCase())
                    .confidence(confidence != null ? Double.parseDouble(confidence) : 50.0)
                    .targetPrice(targetPrice != null ? Double.parseDouble(targetPrice) : null)
                    .stopLoss(stopLoss != null ? Double.parseDouble(stopLoss) : null)
                    .build();
            var signal = decisionSignalService.createSignal(signalEntity);
            StringBuilder sb = new StringBuilder("【决策信号已创建】\n\n");
            sb.append(String.format("股票代码: %s\n", stockCode));
            sb.append(String.format("操作建议: %s\n", action));
            sb.append(String.format("信心度: %s%%\n", confidence));
            if (targetPrice != null) sb.append(String.format("目标价格: %s\n", targetPrice));
            if (stopLoss != null) sb.append(String.format("止损价格: %s\n", stopLoss));
            return sb.toString();
        } catch (Exception e) {
            log.error("创建决策信号失败: {}", stockCode, e);
            return "创建决策信号失败: " + e.getMessage();
        }
    }

    @Tool("查看股票的活跃决策信号")
    public String getActiveSignals(@P("股票代码") String stockCode) {
        log.info("查询活跃信号: {}", stockCode);
        try {
            var signals = decisionSignalService.getActiveSignals(stockCode);
            if (signals == null || signals.isEmpty()) {
                return stockCode + " 当前没有活跃的决策信号";
            }
            StringBuilder sb = new StringBuilder("【活跃决策信号 - " + stockCode + "】\n\n");
            for (var s : signals) {
                sb.append(String.format("信号: %s | 信心度: %s%% | 目标: %s | 止损: %s\n",
                    s.getAction(), s.getConfidence(), s.getTargetPrice(), s.getStopLoss()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("查询活跃信号失败: {}", stockCode, e);
            return "查询活跃信号失败: " + e.getMessage();
        }
    }

    @Tool("查看交易日历和市场状态")
    public String getMarketStatus() {
        log.info("查询市场状态");
        try {
            var info = tradingCalendar.getMarketInfo();
            StringBuilder sb = new StringBuilder("【交易日历与市场状态】\n\n");
            sb.append(String.format("当前时间: %s\n", java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            if (info != null) {
                sb.append(String.format("市场状态: %s\n", info.get("status")));
                sb.append(String.format("交易时段: %s\n", info.get("session")));
                sb.append(String.format("下一个交易日: %s\n", info.get("nextTradeDate")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("查询市场状态失败", e);
            return "查询市场状态暂时不可用: " + e.getMessage();
        }
    }

    @Tool("检查股票预警条件是否触发")
    public String checkAlerts(@P("股票代码") String stockCode) {
        log.info("检查预警: {}", stockCode);
        try {
            java.util.Map<String, Object> alertResult = alertService.checkAlerts(stockCode);
            if (alertResult == null || alertResult.isEmpty()) {
                return stockCode + " 当前没有触发的预警";
            }
            int checked = alertResult.containsKey("checked") ? ((Number) alertResult.get("checked")).intValue() : 0;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> triggered = (List<Map<String, Object>>) alertResult.getOrDefault("triggered", java.util.Collections.emptyList());
            if (triggered.isEmpty()) {
                return String.format("%s 检查了 %d 个预警，当前无触发", stockCode, checked);
            }
            StringBuilder sb = new StringBuilder("【预警信息 - " + stockCode + "】\n\n");
            for (Map<String, Object> alert : triggered) {
                sb.append(String.format("⚠️ %s: %s (当前值: %s)\n", alert.get("alertType"), alert.get("message"), alert.get("currentValue")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("检查预警失败: {}", stockCode, e);
            return "检查预警暂时不可用: " + e.getMessage();
        }
    }
}