package com.stockagent.tools;

import com.stockagent.dto.BacktestResultDTO;
import com.stockagent.dto.TradeReviewDTO;
import com.stockagent.service.BacktestService;
import com.stockagent.service.TradeReviewService;
import com.stockagent.service.WebSearchService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {
    private final WebSearchService webSearchService;
    private final TradeReviewService tradeReviewService;
    private final BacktestService backtestService;

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
        } catch (Exception e) { return "搜索失败：" + e.getMessage(); }
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
        } catch (Exception e) { return "复盘失败：" + e.getMessage(); }
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
            return String.format("【回测结果】\n策略: %s\n收益率: %s%%\n最大回撤: %s%%\n交易次数: %s\n胜率: %s%%",
                r.getStrategyName(), r.getTotalReturn(), r.getMaxDrawdown(), r.getTradeCount(), r.getWinRate());
        } catch (Exception e) { return "回测失败：" + e.getMessage(); }
    }
}
