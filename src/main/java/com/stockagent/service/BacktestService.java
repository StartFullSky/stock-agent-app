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
 * 支持：均线策略、买入持有策略、RSI策略、MACD策略、布林带策略、信号评估
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
    private final TechnicalIndicatorService technicalIndicatorService;

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

    // ======================== RSI 策略回测 ========================
    public BacktestResultDTO backtestRSI(String stockCode, int period, double overbought, double oversold,
                                         String startDate, String endDate, BigDecimal initialCapital) {
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (period <= 0 || overbought <= oversold) {
            throw new IllegalArgumentException("RSI参数不合法：周期应大于0，超买线应大于超卖线");
        }

        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty() || klines.size() < period + 1) {
            return createEmptyResult("RSI策略", stockCode, "数据不足，需要至少" + (period + 1) + "个交易日");
        }

        // 提取收盘价并计算RSI
        double[] closePrices = new double[klines.size()];
        for (int i = 0; i < klines.size(); i++) {
            closePrices[i] = getClosePrice(klines.get(i)).doubleValue();
        }
        double[] rsiValues = technicalIndicatorService.rsi(closePrices, period);

        // 生成买卖信号：RSI上穿超卖线买入，下穿超买线卖出
        boolean[] buySignals = new boolean[klines.size()];
        boolean[] sellSignals = new boolean[klines.size()];
        for (int i = 1; i < klines.size(); i++) {
            if (!Double.isNaN(rsiValues[i]) && !Double.isNaN(rsiValues[i - 1])) {
                if (rsiValues[i - 1] <= oversold && rsiValues[i] > oversold) {
                    buySignals[i] = true;
                }
                if (rsiValues[i - 1] >= overbought && rsiValues[i] < overbought) {
                    sellSignals[i] = true;
                }
            }
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("RSI策略（周期" + period + "，超买" + overbought + "/超卖" + oversold + "）");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("RSI上穿" + oversold + "买入，下穿" + overbought + "卖出");

        return runGenericBacktest(klines, buySignals, sellSignals, initialCapital, result);
    }

    // ======================== MACD 策略回测 ========================
    public BacktestResultDTO backtestMACD(String stockCode, String startDate, String endDate, BigDecimal initialCapital) {
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("股票代码不能为空");
        }

        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty() || klines.size() < 35) {
            return createEmptyResult("MACD策略", stockCode, "数据不足，MACD需要至少35个交易日");
        }

        // 提取收盘价并计算MACD (12, 26, 9)
        double[] closePrices = new double[klines.size()];
        for (int i = 0; i < klines.size(); i++) {
            closePrices[i] = getClosePrice(klines.get(i)).doubleValue();
        }
        List<double[]> macdValues = technicalIndicatorService.macd(closePrices, 12, 26, 9);

        // 生成买卖信号：DIF上穿DEA买入（金叉），DIF下穿DEA卖出（死叉）
        boolean[] buySignals = new boolean[klines.size()];
        boolean[] sellSignals = new boolean[klines.size()];
        for (int i = 1; i < macdValues.size(); i++) {
            double[] prev = macdValues.get(i - 1);
            double[] curr = macdValues.get(i);
            if (Double.isNaN(prev[0]) || Double.isNaN(prev[1]) || Double.isNaN(curr[0]) || Double.isNaN(curr[1])) {
                continue;
            }
            // 金叉：DIF从下方穿越DEA
            if (prev[0] <= prev[1] && curr[0] > curr[1]) {
                buySignals[i] = true;
            }
            // 死叉：DIF从上方穿越DEA
            if (prev[0] >= prev[1] && curr[0] < curr[1]) {
                sellSignals[i] = true;
            }
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("MACD策略（12/26/9）");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("DIF上穿DEA（金叉）买入，下穿DEA（死叉）卖出");

        return runGenericBacktest(klines, buySignals, sellSignals, initialCapital, result);
    }

    // ======================== 布林带策略回测 ========================
    public BacktestResultDTO backtestBollinger(String stockCode, int period, double multiplier,
                                               String startDate, String endDate, BigDecimal initialCapital) {
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("股票代码不能为空");
        }
        if (period <= 0 || multiplier <= 0) {
            throw new IllegalArgumentException("布林带参数不合法：周期和标准差倍数应大于0");
        }

        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty() || klines.size() < period) {
            return createEmptyResult("布林带策略", stockCode, "数据不足，需要至少" + period + "个交易日");
        }

        // 提取收盘价并计算布林带
        double[] closePrices = new double[klines.size()];
        for (int i = 0; i < klines.size(); i++) {
            closePrices[i] = getClosePrice(klines.get(i)).doubleValue();
        }
        double[][] bollValues = technicalIndicatorService.bollinger(closePrices, period, multiplier);
        double[] upper = bollValues[0]; // 上轨
        double[] lower = bollValues[2]; // 下轨

        // 生成买卖信号：价格触及下轨买入，触及上轨卖出
        boolean[] buySignals = new boolean[klines.size()];
        boolean[] sellSignals = new boolean[klines.size()];
        for (int i = period - 1; i < klines.size(); i++) {
            if (Double.isNaN(upper[i]) || Double.isNaN(lower[i])) continue;
            if (closePrices[i] <= lower[i]) {
                buySignals[i] = true;
            }
            if (closePrices[i] >= upper[i]) {
                sellSignals[i] = true;
            }
        }

        BacktestResultDTO result = new BacktestResultDTO();
        result.setStrategyName("布林带策略（周期" + period + "，倍数" + multiplier + "）");
        result.setStockCode(stockCode);
        result.setPeriod(startDate + " ~ " + endDate);
        result.setInitialCapital(initialCapital);
        result.setDescription("价格触及下轨买入，触及上轨卖出");

        return runGenericBacktest(klines, buySignals, sellSignals, initialCapital, result);
    }

    // ======================== 信号评估回测 ========================
    public Map<String, Object> backtestSignalEvaluation(String stockCode, double entryPrice, double targetPrice,
                                                         double stopLoss, String startDate, String endDate) {
        Map<String, Object> evalResult = new LinkedHashMap<>();
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("股票代码不能为空");
        }

        List<Map<String, Object>> klines = fetchKlineData(stockCode, startDate, endDate);
        if (klines.isEmpty()) {
            evalResult.put("signalCorrect", false);
            evalResult.put("maxGain", 0.0);
            evalResult.put("maxLoss", 0.0);
            evalResult.put("hitTarget", false);
            evalResult.put("hitStopLoss", false);
            evalResult.put("description", "无历史数据");
            return evalResult;
        }

        double maxGain = 0.0;
        double maxLoss = 0.0;
        boolean hitTarget = false;
        boolean hitStopLoss = false;

        for (Map<String, Object> kline : klines) {
            double high = getClosePrice(kline).doubleValue();
            double low = getClosePrice(kline).doubleValue();
            try {
                if (kline.get("high") != null) high = Double.parseDouble(kline.get("high").toString());
                if (kline.get("low") != null) low = Double.parseDouble(kline.get("low").toString());
            } catch (Exception ignored) {}

            double gainFromHigh = (high - entryPrice) / entryPrice * 100;
            double lossFromLow = (entryPrice - low) / entryPrice * 100;
            if (gainFromHigh > maxGain) maxGain = gainFromHigh;
            if (lossFromLow > maxLoss) maxLoss = lossFromLow;

            if (high >= targetPrice) hitTarget = true;
            if (low <= stopLoss) hitStopLoss = true;
        }

        // 信号正确性判断
        boolean signalCorrect = hitTarget && !hitStopLoss;
        if (hitTarget && hitStopLoss) {
            double lastClose = getClosePrice(klines.get(klines.size() - 1)).doubleValue();
            signalCorrect = lastClose >= entryPrice;
        }

        evalResult.put("signalCorrect", signalCorrect);
        evalResult.put("maxGain", BigDecimal.valueOf(maxGain).setScale(2, RoundingMode.HALF_UP).doubleValue());
        evalResult.put("maxLoss", BigDecimal.valueOf(maxLoss).setScale(2, RoundingMode.HALF_UP).doubleValue());
        evalResult.put("hitTarget", hitTarget);
        evalResult.put("hitStopLoss", hitStopLoss);
        evalResult.put("entryPrice", entryPrice);
        evalResult.put("targetPrice", targetPrice);
        evalResult.put("stopLoss", stopLoss);
        evalResult.put("dataRange", klines.get(0).get("date") + " ~ " + klines.get(klines.size() - 1).get("date"));
        return evalResult;
    }

    // ======================== 通用回测执行引擎 ========================
    /**
     * 通用回测执行：根据买卖信号数组执行回测，计算收益、回撤等指标
     */
    private BacktestResultDTO runGenericBacktest(List<Map<String, Object>> klines, boolean[] buySignals,
                                                 boolean[] sellSignals, BigDecimal initialCapital,
                                                 BacktestResultDTO result) {
        BigDecimal cash = initialCapital;
        int shares = 0;
        int tradeCount = 0;
        int winCount = 0;
        BigDecimal maxNav = initialCapital;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        List<Map<String, Object>> dailyNav = new ArrayList<>();
        BigDecimal buyPrice = null;

        for (int i = 0; i < klines.size(); i++) {
            BigDecimal closePrice = getClosePrice(klines.get(i));
            String date = getDate(klines.get(i));

            // 执行买入信号
            if (buySignals[i] && shares == 0) {
                int buyShares = cash.divide(closePrice, 0, RoundingMode.DOWN).intValue();
                if (buyShares > 0) {
                    cash = cash.subtract(closePrice.multiply(BigDecimal.valueOf(buyShares)));
                    shares = buyShares;
                    buyPrice = closePrice;
                    tradeCount++;
                }
            }

            // 执行卖出信号
            if (sellSignals[i] && shares > 0) {
                BigDecimal sellAmount = closePrice.multiply(BigDecimal.valueOf(shares));
                if (buyPrice != null && closePrice.compareTo(buyPrice) > 0) {
                    winCount++;
                }
                cash = cash.add(sellAmount);
                shares = 0;
                buyPrice = null;
                tradeCount++;
            }

            // 计算每日净值
            BigDecimal nav = cash.add(closePrice.multiply(BigDecimal.valueOf(shares)));
            if (nav.compareTo(maxNav) > 0) maxNav = nav;
            BigDecimal drawdown = maxNav.subtract(nav).multiply(BigDecimal.valueOf(100))
                    .divide(maxNav, 2, RoundingMode.HALF_UP);
            if (drawdown.compareTo(maxDrawdown) > 0) maxDrawdown = drawdown;

            Map<String, Object> navPoint = new HashMap<>();
            navPoint.put("date", date);
            navPoint.put("nav", nav.setScale(2, RoundingMode.HALF_UP));
            dailyNav.add(navPoint);
        }

        // 未平仓头寸按最后价格强制平仓
        if (shares > 0) {
            BigDecimal lastPrice = getClosePrice(klines.get(klines.size() - 1));
            if (buyPrice != null && lastPrice.compareTo(buyPrice) > 0) {
                winCount++;
            }
            cash = cash.add(lastPrice.multiply(BigDecimal.valueOf(shares)));
            tradeCount++;
        }

        result.setFinalCapital(cash);
        result.setTotalReturn(cash.subtract(initialCapital)
                .multiply(BigDecimal.valueOf(100)).divide(initialCapital, 2, RoundingMode.HALF_UP));
        result.setTradeCount(tradeCount);
        int roundTrips = tradeCount / 2;
        result.setWinCount(winCount);
        if (roundTrips > 0) {
            result.setWinRate(BigDecimal.valueOf(winCount * 100.0 / roundTrips)
                    .setScale(1, RoundingMode.HALF_UP));
        }
        result.setMaxDrawdown(maxDrawdown);
        result.setDailyNav(dailyNav.size() > 100
                ? dailyNav.subList(dailyNav.size() - 100, dailyNav.size()) : dailyNav);
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
