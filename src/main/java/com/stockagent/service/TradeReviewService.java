package com.stockagent.service;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.dto.TradeReviewDTO;
import com.stockagent.entity.StockTrade;
import com.stockagent.mapper.StockTradeMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易复盘服务 - 分析交易行为，提取交易模式
 * 灵感来源：Vibe-Trading Shadow Account
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TradeReviewService {

    private final StockTradeMapper stockTradeMapper;

    /**
     * 分析用户交易行为
     */
    public TradeReviewDTO analyzeTradeBehavior(Long userId) {
        List<StockTrade> trades = stockTradeMapper.selectList(
                new LambdaQueryWrapper<StockTrade>()
                        .eq(StockTrade::getUserId, userId)
                        .orderByDesc(StockTrade::getTradeDate)
        );

        if (trades.isEmpty()) {
            return TradeReviewDTO.empty();
        }

        TradeReviewDTO review = new TradeReviewDTO();
        review.setTotalTrades(trades.size());

        // 买入/卖出分离
        List<StockTrade> buys = trades.stream().filter(t -> t.getTradeType() == 1).collect(Collectors.toList());
        List<StockTrade> sells = trades.stream().filter(t -> t.getTradeType() == 2).collect(Collectors.toList());

        review.setBuyCount(buys.size());
        review.setSellCount(sells.size());

        // 计算总买入/卖出金额
        BigDecimal totalBuyAmount = buys.stream().map(StockTrade::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSellAmount = sells.stream().map(StockTrade::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        review.setTotalBuyAmount(totalBuyAmount);
        review.setTotalSellAmount(totalSellAmount);

        // 交易频率分析
        if (trades.size() >= 2) {
            LocalDate firstDate = trades.get(trades.size() - 1).getTradeDate();
            LocalDate lastDate = trades.get(0).getTradeDate();
            long daysBetween = ChronoUnit.DAYS.between(firstDate, lastDate);
            if (daysBetween > 0) {
                review.setTradeDays((int) daysBetween);
                review.setAvgTradesPerWeek(BigDecimal.valueOf(trades.size() * 7.0 / daysBetween)
                        .setScale(1, RoundingMode.HALF_UP));
            }
        }

        // 持仓股票统计
        Map<String, List<StockTrade>> byStock = trades.stream()
                .collect(Collectors.groupingBy(StockTrade::getStockCode));
        review.setUniqueStocks(byStock.size());

        // 分析每只股票的盈亏
        List<Map<String, Object>> stockProfits = new ArrayList<>();
        BigDecimal totalProfit = BigDecimal.ZERO;
        int winCount = 0;
        int lossCount = 0;

        for (Map.Entry<String, List<StockTrade>> entry : byStock.entrySet()) {
            String code = entry.getKey();
            List<StockTrade> stockTrades = entry.getValue();
            String stockName = stockTrades.get(0).getStockName();

            BigDecimal buyTotal = BigDecimal.ZERO;
            int buyQty = 0;
            BigDecimal sellTotal = BigDecimal.ZERO;
            int sellQty = 0;

            for (StockTrade t : stockTrades) {
                if (t.getTradeType() == 1) {
                    buyTotal = buyTotal.add(t.getAmount());
                    buyQty += t.getQuantity();
                } else {
                    sellTotal = sellTotal.add(t.getAmount());
                    sellQty += t.getQuantity();
                }
            }

            // 只分析已清仓的股票
            if (sellQty > 0 && buyQty > 0) {
                BigDecimal avgBuyPrice = buyTotal.divide(BigDecimal.valueOf(buyQty), 4, RoundingMode.HALF_UP);
                BigDecimal avgSellPrice = sellTotal.divide(BigDecimal.valueOf(sellQty), 4, RoundingMode.HALF_UP);
                BigDecimal profit = sellTotal.subtract(buyTotal.multiply(BigDecimal.valueOf(sellQty)).divide(BigDecimal.valueOf(buyQty), 2, RoundingMode.HALF_UP));

                Map<String, Object> stockProfit = new HashMap<>();
                stockProfit.put("code", code);
                stockProfit.put("name", stockName);
                stockProfit.put("avgBuyPrice", avgBuyPrice);
                stockProfit.put("avgSellPrice", avgSellPrice);
                stockProfit.put("profit", profit);
                stockProfit.put("profitRate", avgSellPrice.subtract(avgBuyPrice).multiply(BigDecimal.valueOf(100)).divide(avgBuyPrice, 2, RoundingMode.HALF_UP));
                stockProfits.add(stockProfit);

                totalProfit = totalProfit.add(profit);
                if (profit.compareTo(BigDecimal.ZERO) > 0) winCount++;
                else lossCount++;
            }
        }

        review.setTotalProfit(totalProfit);
        review.setWinCount(winCount);
        review.setLossCount(lossCount);
        int closedTrades = winCount + lossCount;
        if (closedTrades > 0) {
            review.setWinRate(BigDecimal.valueOf(winCount * 100.0 / closedTrades).setScale(1, RoundingMode.HALF_UP));
        }
        review.setStockProfits(stockProfits);

        // 交易行为诊断
        List<String> diagnoses = new ArrayList<>();
        if (review.getAvgTradesPerWeek() != null && review.getAvgTradesPerWeek().compareTo(BigDecimal.valueOf(5)) > 0) {
            diagnoses.add("⚠️ 交易频率过高（平均每周" + review.getAvgTradesPerWeek() + "次），可能存在过度交易");
        }
        if (review.getWinRate() != null && review.getWinRate().compareTo(BigDecimal.valueOf(40)) < 0) {
            diagnoses.add("⚠️ 胜率偏低（" + review.getWinRate() + "%），建议优化选股或入场时机");
        }
        if (review.getUniqueStocks() > 10) {
            diagnoses.add("⚠️ 持仓过于分散（" + review.getUniqueStocks() + "只股票），建议集中精力研究少数标的");
        }
        if (review.getWinRate() != null && review.getWinRate().compareTo(BigDecimal.valueOf(60)) > 0) {
            diagnoses.add("✅ 胜率优秀（" + review.getWinRate() + "%），继续保持");
        }
        if (review.getTotalProfit().compareTo(BigDecimal.ZERO) > 0) {
            diagnoses.add("✅ 总体盈利 " + review.getTotalProfit() + " 元");
        }
        review.setDiagnoses(diagnoses);

        return review;
    }
}
