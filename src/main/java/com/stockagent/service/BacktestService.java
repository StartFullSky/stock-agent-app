package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stockagent.dto.BacktestResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 回测服务
 * 支持：均线策略、买入持有策略
 * 数据源：腾讯行情API（免费）
 */
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

@Service
@Slf4j
@RequiredArgsConstructor
public class BacktestService {

    private static final String TENCENT_KLINE_URL = "http://web.ifzq.gtimg.cn/appstock/app/fqkline/get";

    @Value("${http.timeout.kline:10000}")
    private int httpTimeout;

    private final StockService stockService;

    public BacktestResultDTO backtestMovingAverage(String stockCode, int shortPeriod, int longPeriod,
                                                   String startDate, String endDate, BigDecimal initialCapital) {
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (shortPeriod <= 0 || longPeriod <= 0 || shortPeriod >= longPeriod) {
            throw new IllegalArgumentException("均线周期参数不合法，短期周期应小于长期周期");
        }

        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty() || klines.size() < longPeriod) {
            return createEmptyResult("双均线策略", stockCode, "数据不足，需要至少" + longPeriod + "个交易日");
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("双均线策略（MA" + shortPeriod + "/MA" + longPeriod + "）");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("当" + shortPeriod + "日均线上穿" + longPeriod + "日均线时买入，下穿时卖出");

        return runBacktest(klines, shortPeriod, longPeriod, initialCapital, result);
    }

    public BacktestResultDTO backtestBuyAndHold(String stockCode, String startDate, String endDate, BigDecimal initialCapital) {
        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty()) return createEmptyResult("买入持有", stockCode, "数据不足");

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("买入持有策略");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("第一天全仓买入，最后一天卖出");

        BigDecimal firstPrice = getClosePrice(klines.get(0));
        BigDecimal lastPrice = getClosePrice(klines.get(klines.size() - 1));
        if (firstPrice.compareTo(BigDecimal.ZERO) == 0) return createEmptyResult("买入持有", stockCode, "初始价格为零");

        int shares = initialCapital.divide(firstPrice, 0, RoundingMode.DOWN).intValue();
        BigDecimal cost = firstPrice.multiply(BigDecimal.valueOf(shares));
        BigDecimal finalValue = lastPrice.multiply(BigDecimal.valueOf(shares));
        BigDecimal profit = finalValue.subtract(cost);

        result.setFinalCapital(initialCapital.subtract(cost).add(finalValue));
        result.setTotalReturn(profit.multiply(BigDecimal.valueOf(100)).divide(cost, 2, RoundingMode.HALF_UP));
        result.setTradeCount(2);
        result.setWinCount(profit.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        result.setWinRate(profit.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO);
        return result;
    }

    private BacktestResultDTO runBacktest(List<Map<String, Object>> klines, int shortPeriod, int longPeriod,
                                          BigDecimal initialCapital, BacktestResultDTO result) {
        BigDecimal cash = initialCapital;
        int shares = 0;
        int tradeCount = 0;
        int winCount = 0;
        BigDecimal maxNav = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        List<Map<String, Object>> dailyNav = new ArrayList<>();
        BigDecimal buyPrice = null; // 记录买入价格，用于判断单笔交易盈亏

        for (int i = longPeriod; i < klines.size(); i++) {
            BigDecimal shortMa = calculateMA(klines, i, shortPeriod);
            BigDecimal longMa = calculateMA(klines, i, longPeriod);
            BigDecimal prevShortMa = calculateMA(klines, i - 1, shortPeriod);
            BigDecimal prevLongMa = calculateMA(klines, i - 1, longPeriod);
            BigDecimal closePrice = getClosePrice(klines.get(i));
            String date = getDate(klines.get(i));

            // 金叉买入
            if (prevShortMa.compareTo(prevLongMa) <= 0 && shortMa.compareTo(longMa) > 0 && shares == 0) {
                int buyShares = cash.divide(closePrice, 0, RoundingMode.DOWN).intValue();
                if (buyShares > 0) {
                    cash = cash.subtract(closePrice.multiply(BigDecimal.valueOf(buyShares)));
                    shares = buyShares;
                    buyPrice = closePrice; // 记录买入价格
                    tradeCount++;
                }
            }

            // 死叉卖出
            if (prevShortMa.compareTo(prevLongMa) >= 0 && shortMa.compareTo(longMa) < 0 && shares > 0) {
                BigDecimal sellAmount = closePrice.multiply(BigDecimal.valueOf(shares));
                // 判断本次卖出是否盈利：卖出价 > 买入价
                if (buyPrice != null && closePrice.compareTo(buyPrice) > 0) {
                    winCount++;
                }
                cash = cash.add(sellAmount);
                shares = 0;
                buyPrice = null;
                tradeCount++;
            }

            BigDecimal nav = cash.add(closePrice.multiply(BigDecimal.valueOf(shares)));
            if (nav.compareTo(maxNav) > 0) maxNav = nav;
            BigDecimal drawdown = maxNav.subtract(nav).multiply(BigDecimal.valueOf(100)).divide(maxNav, 2, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;

            Map<String, Object> navPoint = new HashMap<>();
            navPoint.put("date", date);
            navPoint.put("nav", nav.setScale(2, RoundingMode.HALF_UP));
            dailyNav.add(navPoint);
        }

        if (shares > 0) {
            BigDecimal lastPrice = getClosePrice(klines.get(klines.size() - 1));
            // 未平仓头寸按最后价格强制平仓，计入交易次数和胜负
            if (buyPrice != null && lastPrice.compareTo(buyPrice) > 0) {
                winCount++;
            }
            cash = cash.add(lastPrice.multiply(BigDecimal.valueOf(shares)));
            tradeCount++;
        }

        result.setFinalCapital(cash);
        result.setTotalReturn(cash.subtract(initialCapital).multiply(BigDecimal.valueOf(100)).divide(initialCapital, 2, RoundingMode.HALF_UP));
        result.setTradeCount(tradeCount);
        // 完成的交易轮次 = tradeCount / 2（买+卖算一轮）
        int roundTrips = tradeCount / 2;
        result.setWinCount(winCount);
        if (roundTrips > 0) {
            result.setWinRate(BigDecimal.valueOf(winCount * 100.0 / roundTrips).setScale(1, RoundingMode.HALF_UP));
        }
        result.setMaxDrawdown(maxDrawdown);
        result.setDailyNav(dailyNav.size() > 100 ? dailyNav.subList(dailyNav.size() - 100, dailyNav.size()) : dailyNav);
        return result;
    }

    private List<Map<String, Object>> fetchKlineData(String stockCode, String startDate, String endDate) {
        List<Map<String, Object>> klines = new ArrayList<>();
        try {
            // 使用正确的市场代码判断，复用StockService的市场检测逻辑
            String code = stockService.toTencentCode(stockCode);
            String url = TENCENT_KLINE_URL + "?param=" + code + ",day," + startDate + "," + endDate + ",500,qfq";
            String response = HttpUtil.get(url, httpTimeout);
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
                    kline.put("date", day.getStr(0)); kline.put("open", day.getStr(1));
                    kline.put("close", day.getStr(2)); kline.put("high", day.getStr(3)); kline.put("low", day.getStr(4));
                    klines.add(kline);
                }
            }
        } catch (Exception e) { log.error("获取K线数据失败", e); }
        return klines;
    }



    private BigDecimal calculateMA(List<Map<String, Object>> klines, int endIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO; int count = 0;
        for (int i = endIndex - period + 1; i <= endIndex; i++) {
            if (i >= 0 && i < klines.size()) { sum = sum.add(getClosePrice(klines.get(i))); count++; }
        }
        return count > 0 ? sum.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private BigDecimal getClosePrice(Map<String, Object> kline) {
        try { return new BigDecimal(kline.get("close").toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String getDate(Map<String, Object> kline) {
        return kline.get("date") != null ? kline.get("date").toString() : "";
    }

    private BacktestResultDTO createEmptyResult(String strategy, String code, String reason) {
        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName(strategy); result.setStockCode(code);
        result.setDescription("回测失败：" + reason);
        result.setTotalReturn(BigDecimal.ZERO); result.setTradeCount(0);
        return result;
    }
}
