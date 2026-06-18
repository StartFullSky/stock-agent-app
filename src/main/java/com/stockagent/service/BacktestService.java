package com.stockagent.service;
import lombok.extern.slf4j.Slf4j;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stockagent.dto.BacktestResultDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 简单回测服务
 * 支持：均线策略、买入持有策略
 * 数据源：腾讯行情API（免费）
 */
@Service
@Slf4j
public class BacktestService {

    private static final String TENCENT_KLINE_URL = "http://web.ifzq.gtimg.cn/appstock/app/fqkline/get";

    /**
     * 双均线策略回测
     * @param stockCode 股票代码
     * @param shortPeriod 短期均线周期（如5）
     * @param longPeriod 长期均线周期（如20）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param initialCapital 初始资金
     */
    public BacktestResultDTO backtestMovingAverage(String stockCode, int shortPeriod, int longPeriod,
                                                   String startDate, String endDate, BigDecimal initialCapital) {
        // 获取历史K线数据
        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty() || klines.size() < longPeriod) {
            return createEmptyResult("双均线策略", stockCode, "数据不足");
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("双均线策略（MA" + shortPeriod + "/MA" + longPeriod + "）");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("当" + shortPeriod + "日均线上穿" + longPeriod + "日均线时买入，下穿时卖出");

        return runBacktest(klines, shortPeriod, longPeriod, initialCapital, result);
    }

    /**
     * 买入持有策略回测
     */
    public BacktestResultDTO backtestBuyAndHold(String stockCode, String startDate, String endDate, BigDecimal initialCapital) {
        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty()) {
            return createEmptyResult("买入持有", stockCode, "数据不足");
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("买入持有策略");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("第一天全仓买入，最后一天卖出");

        BigDecimal firstPrice = getClosePrice(klines.get(0));
        BigDecimal lastPrice = getClosePrice(klines.get(klines.size() - 1));

        int shares = initialCapital.divide(firstPrice, 0, RoundingMode.DOWN).intValue();
        BigDecimal cost = initialCapital.multiply(BigDecimal.valueOf(shares));
        BigDecimal finalValue = lastPrice.multiply(BigDecimal.valueOf(shares));
        BigDecimal profit = finalValue.subtract(cost);

        result.setFinalCapital(initialCapital.subtract(cost).add(finalValue));
        result.setTotalReturn(profit.multiply(BigDecimal.valueOf(100)).divide(cost, 2, RoundingMode.HALF_UP));
        result.setTradeCount(2); // 买一次卖一次
        result.setWinCount(profit.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        result.setWinRate(profit.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);

        return result;
    }

    /**
     * 运行双均线回测
     */
    private BacktestResultDTO runBacktest(List<Map<String, Object>> klines, int shortPeriod, int longPeriod,
                                          BigDecimal initialCapital, BacktestResultDTO result) {
        BigDecimal cash = initialCapital;
        int shares = 0;
        int tradeCount = 0;
        int winCount = 0;
        BigDecimal maxNav = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        List<Map<String, Object>> dailyNav = new ArrayList<>();

        for (int i = longPeriod; i < klines.size(); i++) {
            // 计算均线
            BigDecimal shortMa = calculateMA(klines, i, shortPeriod);
            BigDecimal longMa = calculateMA(klines, i, longPeriod);
            BigDecimal prevShortMa = calculateMA(klines, i - 1, shortPeriod);
            BigDecimal prevLongMa = calculateMA(klines, i - 1, longPeriod);
            BigDecimal closePrice = getClosePrice(klines.get(i));
            String date = getDate(klines.get(i));

            // 金叉买入
            if (prevShortMa.compareTo(prevLongMa) <= 0 && shortMa.compareTo(longMa) > 0 && shares == 0) {
                shares = cash.divide(closePrice, 0, RoundingMode.DOWN).intValue();
                if (shares > 0) {
                    BigDecimal cost = closePrice.multiply(BigDecimal.valueOf(shares));
                    cash = cash.subtract(cost);
                    tradeCount++;
                }
            }
            // 死叉卖出
            else if (prevShortMa.compareTo(prevLongMa) >= 0 && shortMa.compareTo(longMa) < 0 && shares > 0) {
                BigDecimal sellAmount = closePrice.multiply(BigDecimal.valueOf(shares));
                if (sellAmount.compareTo(cash.add(sellAmount).subtract(initialCapital)) > 0) {
                    winCount++;
                }
                cash = cash.add(sellAmount);
                shares = 0;
                tradeCount++;
            }

            // 计算净值
            BigDecimal nav = cash.add(closePrice.multiply(BigDecimal.valueOf(shares)));
            if (nav.compareTo(maxNav) > 0) maxNav = nav;
            BigDecimal drawdown = maxNav.subtract(nav).multiply(BigDecimal.valueOf(100)).divide(maxNav, 2, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;

            Map<String, Object> navPoint = new HashMap<>();
            navPoint.put("date", date);
            navPoint.put("nav", nav.setScale(2, RoundingMode.HALF_UP));
            dailyNav.add(navPoint);
        }

        // 最终清仓
        if (shares > 0) {
            BigDecimal lastPrice = getClosePrice(klines.get(klines.size() - 1));
            cash = cash.add(lastPrice.multiply(BigDecimal.valueOf(shares)));
        }

        result.setFinalCapital(cash);
        result.setTotalReturn(cash.subtract(initialCapital).multiply(BigDecimal.valueOf(100)).divide(initialCapital, 2, RoundingMode.HALF_UP));
        result.setTradeCount(tradeCount);
        result.setWinCount(winCount / 2); // 买入卖出配对
        if (tradeCount > 0) {
            result.setWinRate(BigDecimal.valueOf(winCount * 100.0 / tradeCount).setScale(1, RoundingMode.HALF_UP));
        }
        result.setMaxDrawdown(maxDrawdown);
        result.setDailyNav(dailyNav.size() > 100 ? dailyNav.subList(dailyNav.size() - 100, dailyNav.size()) : dailyNav);

        return result;
    }

    /**
     * 获取K线数据（腾讯API）
     */
    private List<Map<String, Object>> fetchKlineData(String stockCode, String startDate, String endDate) {
        List<Map<String, Object>> klines = new ArrayList<>();
        try {
            String prefix = stockCode.startsWith("6") ? "sh" : "sz";
            String code = prefix + stockCode;
            String url = TENCENT_KLINE_URL + "?param=" + code + ",day," + startDate + "," + endDate + ",500,qfq";

            String response = HttpUtil.get(url, 10000);
            if (StrUtil.isBlank(response)) return klines;

            JSONObject json = JSONUtil.parseObj(response);
            JSONObject data = json.getJSONObject("data");
            if (data == null) return klines;

            JSONObject stockData = data.getJSONObject(code);
            if (stockData == null) return klines;

            JSONArray dayArr = stockData.getJSONArray("qfqday");
            if (dayArr == null) dayArr = stockData.getJSONArray("day");
            if (dayArr == null) return klines;

            for (int i = 0; i < dayArr.size(); i++) {
                JSONArray day = dayArr.getJSONArray(i);
                if (day.size() >= 5) {
                    Map<String, Object> kline = new HashMap<>();
                    kline.put("date", day.getStr(0));
                    kline.put("open", day.getStr(1));
                    kline.put("close", day.getStr(2));
                    kline.put("high", day.getStr(3));
                    kline.put("low", day.getStr(4));
                    klines.add(kline);
                }
            }
        } catch (Exception e) {
            log.error("获取K线数据失败: {}", e.getMessage());
        }
        return klines;
    }

    private BigDecimal calculateMA(List<Map<String, Object>> klines, int endIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (i >= 0 && i < klines.size()) {
                sum = sum.add(getClosePrice(klines.get(i)));
                count++;
            }
        }
        return count > 0 ? sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal getClosePrice(Map<String, Object> kline) {
        try {
            return new BigDecimal(kline.get("close").toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String getDate(Map<String, Object> kline) {
        return kline.get("date") != null ? kline.get("date").toString() : "";
    }

    private BacktestResultDTO createEmptyResult(String strategy, String code, String reason) {
        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName(strategy);
        result.setStockCode(code);
        result.setDescription("回测失败：" + reason);
        result.setTotalReturn(BigDecimal.ZERO);
        result.setTradeCount(0);
        return result;
    }
}
