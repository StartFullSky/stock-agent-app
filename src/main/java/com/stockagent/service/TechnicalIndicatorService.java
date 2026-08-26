package com.stockagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 技术指标计算服务
 * 纯计算引擎，提供常用股票技术指标的计算方法
 * 移植自 Python daily_stock_analysis
 */
@Slf4j
@Service
public class TechnicalIndicatorService {

    // ======================== SMA - 简单移动平均线 ========================

    /**
     * 简单移动平均线 (Simple Moving Average)
     *
     * @param data   价格序列
     * @param period 计算周期
     * @return SMA数组，前 period-1 个位置为 NaN
     */
    public double[] sma(double[] data, int period) {
        if (data == null || data.length == 0 || period <= 0) {
            return new double[0];
        }
        double[] result = new double[data.length];
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
            if (i >= period) {
                sum -= data[i - period];
            }
            if (i < period - 1) {
                result[i] = Double.NaN;
            } else {
                result[i] = sum / period;
            }
        }
        return result;
    }

    // ======================== EMA - 指数移动平均线 ========================

    /**
     * 指数移动平均线 (Exponential Moving Average)
     * 使用标准 EMA 计算：EMA_today = (close - EMA_yesterday) * multiplier + EMA_yesterday
     * 首个 EMA 值使用前 period 个数据的 SMA
     *
     * @param data   价格序列
     * @param period 计算周期
     * @return EMA数组，前 period-1 个位置为 NaN
     */
    public double[] ema(double[] data, int period) {
        if (data == null || data.length == 0 || period <= 0) {
            return new double[0];
        }
        double[] result = new double[data.length];
        double multiplier = 2.0 / (period + 1);

        // 计算首个 EMA 值（用前 period 个数据的 SMA 作为初始值）
        double sum = 0;
        for (int i = 0; i < Math.min(period, data.length); i++) {
            sum += data[i];
            result[i] = Double.NaN;
        }
        if (data.length >= period) {
            double emaValue = sum / period;
            for (int i = period - 1; i < data.length; i++) {
                if (i == period - 1) {
                    result[i] = emaValue;
                } else {
                    emaValue = (data[i] - emaValue) * multiplier + emaValue;
                    result[i] = emaValue;
                }
            }
        }
        return result;
    }

    // ======================== MACD - 指数平滑异同移动平均线 ========================

    /**
     * MACD 指标计算
     * DIF = EMA(fast) - EMA(slow)
     * DEA (Signal) = EMA(DIF, signalPeriod)
     * MACD Histogram = (DIF - DEA) * 2
     *
     * @param closePrices  收盘价序列
     * @param fastPeriod   快线周期，默认12
     * @param slowPeriod   慢线周期，默认26
     * @param signalPeriod 信号线周期，默认9
     * @return List of double[3]，每个元素为 [DIF, DEA, MACD柱]
     *         对应 Python 返回的 tuple(macd_line, signal_line, histogram)
     */
    public List<double[]> macd(double[] closePrices, int fastPeriod, int slowPeriod, int signalPeriod) {
        List<double[]> result = new ArrayList<>();
        if (closePrices == null || closePrices.length < slowPeriod) {
            return result;
        }

        double[] fastEma = ema(closePrices, fastPeriod);
        double[] slowEma = ema(closePrices, slowPeriod);

        // DIF = fastEMA - slowEMA
        double[] dif = new double[closePrices.length];
        for (int i = 0; i < closePrices.length; i++) {
            if (Double.isNaN(fastEma[i]) || Double.isNaN(slowEma[i])) {
                dif[i] = Double.NaN;
            } else {
                dif[i] = fastEma[i] - slowEma[i];
            }
        }

        // DEA = EMA(DIF, signalPeriod)
        // 需要从第一个非 NaN 的 DIF 开始计算
        int difStart = -1;
        for (int i = 0; i < dif.length; i++) {
            if (!Double.isNaN(dif[i])) {
                difStart = i;
                break;
            }
        }

        double[] dea = new double[closePrices.length];
        Arrays.fill(dea, Double.NaN);

        if (difStart >= 0) {
            // 提取有效的 DIF 部分
            double[] validDif = new double[dif.length - difStart];
            for (int i = difStart; i < dif.length; i++) {
                validDif[i - difStart] = dif[i];
            }

            double[] deaPart = ema(validDif, signalPeriod);
            for (int i = 0; i < deaPart.length; i++) {
                dea[difStart + i] = deaPart[i];
            }
        }

        // MACD柱 = (DIF - DEA) * 2
        for (int i = 0; i < closePrices.length; i++) {
            if (Double.isNaN(dif[i]) || Double.isNaN(dea[i])) {
                result.add(new double[]{Double.NaN, Double.NaN, Double.NaN});
            } else {
                double histogram = (dif[i] - dea[i]) * 2;
                result.add(new double[]{dif[i], dea[i], histogram});
            }
        }

        return result;
    }

    // ======================== RSI - 相对强弱指数 ========================

    /**
     * 相对强弱指数 (Relative Strength Index)
     * 使用指数加权移动平均方法计算 RS
     *
     * @param closePrices 收盘价序列
     * @param period      计算周期，默认14
     * @return RSI数组，前 period 个位置为 NaN，值范围 0-100
     */
    public double[] rsi(double[] closePrices, int period) {
        if (closePrices == null || closePrices.length < period + 1) {
            return new double[closePrices != null ? closePrices.length : 0];
        }

        double[] result = new double[closePrices.length];
        Arrays.fill(result, Double.NaN);

        // 计算价格变动
        double[] changes = new double[closePrices.length];
        for (int i = 1; i < closePrices.length; i++) {
            changes[i] = closePrices[i] - closePrices[i - 1];
        }

        // 初始的平均涨幅和平均跌幅（使用前 period 个变动的简单平均）
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            if (changes[i] > 0) {
                avgGain += changes[i];
            } else {
                avgLoss += Math.abs(changes[i]);
            }
        }
        avgGain /= period;
        avgLoss /= period;

        // 第一个 RSI
        if (avgLoss == 0) {
            result[period] = 100;
        } else {
            double rs = avgGain / avgLoss;
            result[period] = 100 - (100 / (1 + rs));
        }

        // 后续 RSI 使用指数加权
        for (int i = period + 1; i < closePrices.length; i++) {
            double gain = changes[i] > 0 ? changes[i] : 0;
            double loss = changes[i] < 0 ? Math.abs(changes[i]) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            if (avgLoss == 0) {
                result[i] = 100;
            } else {
                double rs = avgGain / avgLoss;
                result[i] = 100 - (100 / (1 + rs));
            }
        }

        return result;
    }

    // ======================== KDJ - 随机指标 ========================

    /**
     * KDJ 随机指标
     * RSV = (Close - Low_N) / (High_N - Low_N) * 100
     * K = SMA(RSV, M1)
     * D = SMA(K, M2)
     * J = 3*K - 2*D
     *
     * @param high   最高价序列
     * @param low    最低价序列
     * @param close  收盘价序列
     * @param period N日周期，默认9
     * @return double[3] = {K数组, D数组, J数组}
     */
    public double[][] kdj(double[] high, double[] low, double[] close, int period) {
        int len = close != null ? close.length : 0;
        double[] kValues = new double[len];
        double[] dValues = new double[len];
        double[] jValues = new double[len];

        if (len < period) {
            return new double[][]{kValues, dValues, jValues};
        }

        // 默认参数 M1=3, M2=3
        int m1 = 3;
        int m2 = 3;

        for (int i = 0; i < len; i++) {
            if (i < period - 1) {
                kValues[i] = 50; // 默认值
                dValues[i] = 50;
                jValues[i] = 50;
                continue;
            }

            // 计算 N 日最高价和最低价
            double highestHigh = Double.NEGATIVE_INFINITY;
            double lowestLow = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                if (high[j] > highestHigh) highestHigh = high[j];
                if (low[j] < lowestLow) lowestLow = low[j];
            }

            // RSV
            double rsv;
            if (highestHigh == lowestLow) {
                rsv = 50;
            } else {
                rsv = (close[i] - lowestLow) / (highestHigh - lowestLow) * 100;
            }

            // K = SMA(RSV, M1)，使用递推方式
            if (i == period - 1) {
                kValues[i] = rsv;
            } else {
                kValues[i] = (2.0 / m1) * rsv + (1 - 2.0 / m1) * kValues[i - 1];
            }

            // D = SMA(K, M2)
            if (i == period - 1) {
                dValues[i] = kValues[i];
            } else {
                dValues[i] = (2.0 / m2) * kValues[i] + (1 - 2.0 / m2) * dValues[i - 1];
            }

            // J = 3K - 2D
            jValues[i] = 3 * kValues[i] - 2 * dValues[i];
        }

        return new double[][]{kValues, dValues, jValues};
    }

    // ======================== Bollinger Bands - 布林带 ========================

    /**
     * 布林带 (Bollinger Bands)
     * Middle = SMA(close, period)
     * Upper = Middle + multiplier * StdDev
     * Lower = Middle - multiplier * StdDev
     *
     * @param closePrices 收盘价序列
     * @param period      计算周期，默认20
     * @param multiplier  标准差倍数，默认2.0
     * @return double[3] = {上轨, 中轨, 下轨}
     */
    public double[][] bollinger(double[] closePrices, int period, double multiplier) {
        int len = closePrices != null ? closePrices.length : 0;
        double[] upper = new double[len];
        double[] middle = new double[len];
        double[] lower = new double[len];

        double[] ma = sma(closePrices, period);

        for (int i = 0; i < len; i++) {
            if (Double.isNaN(ma[i])) {
                upper[i] = Double.NaN;
                middle[i] = Double.NaN;
                lower[i] = Double.NaN;
                continue;
            }

            // 计算标准差
            double sumSq = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = closePrices[j] - ma[i];
                sumSq += diff * diff;
            }
            double stdDev = Math.sqrt(sumSq / period);

            middle[i] = ma[i];
            upper[i] = ma[i] + multiplier * stdDev;
            lower[i] = ma[i] - multiplier * stdDev;
        }

        return new double[][]{upper, middle, lower};
    }

    // ======================== ATR - 平均真实波幅 ========================

    /**
     * 平均真实波幅 (Average True Range)
     * TR = max(H-L, |H-Cprev|, |L-Cprev|)
     * ATR = SMA(TR, period) 或 EMA 方式
     * 此处使用 Wilder 平滑方法（递推）
     *
     * @param high    最高价序列
     * @param low     最低价序列
     * @param close   收盘价序列
     * @param period  计算周期，默认14
     * @return ATR数组，前 period 个位置为 NaN
     */
    public double[] atr(double[] high, double[] low, double[] close, int period) {
        int len = close != null ? close.length : 0;
        double[] result = new double[len];

        if (len < 2) {
            Arrays.fill(result, Double.NaN);
            return result;
        }

        // 计算 TR
        double[] tr = new double[len];
        tr[0] = high[0] - low[0];
        for (int i = 1; i < len; i++) {
            double hl = high[i] - low[i];
            double hc = Math.abs(high[i] - close[i - 1]);
            double lc = Math.abs(low[i] - close[i - 1]);
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }

        // 前 period-1 个为 NaN
        for (int i = 0; i < period - 1 && i < len; i++) {
            result[i] = Double.NaN;
        }

        // 第一个 ATR 为前 period 个 TR 的简单平均
        if (len >= period) {
            double sum = 0;
            for (int i = 0; i < period; i++) {
                sum += tr[i];
            }
            result[period - 1] = sum / period;

            // 后续使用 Wilder 平滑: ATR = (ATR_prev * (period-1) + TR) / period
            for (int i = period; i < len; i++) {
                result[i] = (result[i - 1] * (period - 1) + tr[i]) / period;
            }
        }

        return result;
    }

    // ======================== 综合计算 ========================

    /**
     * 从 K线数据计算所有技术指标
     * 每个 K线 map 包含: date, open, high, low, close, volume
     *
     * @param klineData K线数据列表
     * @return 包含所有指标结果的 Map
     */
    public Map<String, Object> calculateAll(List<Map<String, Object>> klineData) {
        Map<String, Object> indicators = new LinkedHashMap<>();

        if (klineData == null || klineData.isEmpty()) {
            return indicators;
        }

        int size = klineData.size();

        // 提取价格数组
        double[] closePrices = new double[size];
        double[] highPrices = new double[size];
        double[] lowPrices = new double[size];
        double[] openPrices = new double[size];
        double[] volumes = new double[size];
        List<String> dates = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            Map<String, Object> kline = klineData.get(i);
            closePrices[i] = parseDouble(kline.get("close"));
            highPrices[i] = parseDouble(kline.get("high"));
            lowPrices[i] = parseDouble(kline.get("low"));
            openPrices[i] = parseDouble(kline.get("open"));
            volumes[i] = parseDouble(kline.get("volume"));
            Object date = kline.get("date");
            dates.add(date != null ? date.toString() : "");
        }

        indicators.put("dates", dates);

        // ---- SMA ----
        indicators.put("ma5", toList(sma(closePrices, 5)));
        indicators.put("ma10", toList(sma(closePrices, 10)));
        indicators.put("ma20", toList(sma(closePrices, 20)));
        indicators.put("ma60", toList(sma(closePrices, 60)));

        // ---- EMA ----
        indicators.put("ema12", toList(ema(closePrices, 12)));
        indicators.put("ema26", toList(ema(closePrices, 26)));

        // ---- MACD ----
        List<double[]> macdResult = macd(closePrices, 12, 26, 9);
        List<Double> difList = new ArrayList<>();
        List<Double> deaList = new ArrayList<>();
        List<Double> macdHistList = new ArrayList<>();
        for (double[] item : macdResult) {
            difList.add(item[0]);
            deaList.add(item[1]);
            macdHistList.add(item[2]);
        }
        indicators.put("macd_dif", difList);
        indicators.put("macd_dea", deaList);
        indicators.put("macd_hist", macdHistList);

        // ---- RSI ----
        indicators.put("rsi6", toList(rsi(closePrices, 6)));
        indicators.put("rsi12", toList(rsi(closePrices, 12)));
        indicators.put("rsi24", toList(rsi(closePrices, 24)));

        // ---- KDJ ----
        double[][] kdjResult = kdj(highPrices, lowPrices, closePrices, 9);
        indicators.put("kdj_k", toList(kdjResult[0]));
        indicators.put("kdj_d", toList(kdjResult[1]));
        indicators.put("kdj_j", toList(kdjResult[2]));

        // ---- Bollinger Bands ----
        double[][] bollResult = bollinger(closePrices, 20, 2);
        indicators.put("boll_upper", toList(bollResult[0]));
        indicators.put("boll_middle", toList(bollResult[1]));
        indicators.put("boll_lower", toList(bollResult[2]));

        // ---- ATR ----
        indicators.put("atr14", toList(atr(highPrices, lowPrices, closePrices, 14)));

        // ---- 成交量 MA ----
        indicators.put("vol_ma5", toList(sma(volumes, 5)));
        indicators.put("vol_ma10", toList(sma(volumes, 10)));

        // ---- 原始 OHLCV 数据（方便前端绘制） ----
        indicators.put("open", toList(openPrices));
        indicators.put("high", toList(highPrices));
        indicators.put("low", toList(lowPrices));
        indicators.put("close", toList(closePrices));
        indicators.put("volume", toList(volumes));

        return indicators;
    }

    // ======================== 工具方法 ========================

    /**
     * 安全解析 double 值
     */
    private double parseDouble(Object value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析数值: {}", value);
            return 0;
        }
    }

    /**
     * double[] 转为 List<Double>，用于 JSON 序列化
     */
    private List<Double> toList(double[] array) {
        if (array == null) return Collections.emptyList();
        List<Double> list = new ArrayList<>(array.length);
        for (double v : array) {
            list.add(v);
        }
        return list;
    }
}
