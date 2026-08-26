package com.stockagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.dto.StockQuoteDTO;
import com.stockagent.entity.StockAlert;
import com.stockagent.enums.AlertType;
import com.stockagent.mapper.StockAlertMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 股票预警服务
 */
@Slf4j
@Service
public class AlertService {

    @Autowired
    private StockAlertMapper stockAlertMapper;

    @Autowired
    private StockService stockService;

    @Autowired
    private TechnicalIndicatorService technicalIndicatorService;

    /**
     * 创建预警
     */
    public StockAlert createAlert(StockAlert alert) {
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        if (alert.getEnabled() == null) {
            alert.setEnabled(true);
        }
        if (alert.getCooldownMinutes() == null) {
            alert.setCooldownMinutes(60);
        }
        stockAlertMapper.insert(alert);
        log.info("创建预警成功: stockCode={}, alertType={}, threshold={}",
                alert.getStockCode(), alert.getAlertType(), alert.getThreshold());
        return alert;
    }

    /**
     * 根据股票代码查询预警列表
     */
    public List<StockAlert> getAlertsByStock(String stockCode) {
        LambdaQueryWrapper<StockAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StockAlert::getStockCode, stockCode)
                .orderByDesc(StockAlert::getCreatedAt);
        return stockAlertMapper.selectList(wrapper);
    }

    /**
     * 删除预警
     */
    public boolean deleteAlert(String alertId) {
        int result = stockAlertMapper.deleteById(alertId);
        return result > 0;
    }

    /**
     * 检查指定股票的所有已启用预警
     */
    public Map<String, Object> checkAlerts(String stockCode) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> triggered = new ArrayList<>();

        LambdaQueryWrapper<StockAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StockAlert::getStockCode, stockCode)
                .eq(StockAlert::getEnabled, true);
        List<StockAlert> alerts = stockAlertMapper.selectList(wrapper);

        if (alerts == null || alerts.isEmpty()) {
            result.put("triggered", triggered);
            result.put("checked", 0);
            return result;
        }

        LocalDateTime now = LocalDateTime.now();

        for (StockAlert alert : alerts) {
            try {
                if (alert.getLastTriggeredAt() != null && alert.getCooldownMinutes() != null) {
                    long minutesSinceLastTrigger = java.time.Duration.between(
                            alert.getLastTriggeredAt(), now).toMinutes();
                    if (minutesSinceLastTrigger < alert.getCooldownMinutes()) {
                        continue;
                    }
                }

                boolean isTriggered = evaluateAlert(alert);
                if (isTriggered) {
                    Map<String, Object> alertResult = new HashMap<>();
                    alertResult.put("alertId", alert.getId());
                    alertResult.put("alertType", alert.getAlertType());
                    alertResult.put("message", alert.getMessage());
                    alertResult.put("currentValue", getCurrentValue(alert));
                    triggered.add(alertResult);

                    alert.setLastTriggeredAt(now);
                    alert.setUpdatedAt(now);
                    stockAlertMapper.updateById(alert);
                    log.info("预警触发: alertId={}, stockCode={}, type={}",
                            alert.getId(), stockCode, alert.getAlertType());
                }
            } catch (Exception e) {
                log.error("检查预警异常: alertId={}, stockCode={}", alert.getId(), stockCode, e);
            }
        }

        result.put("triggered", triggered);
        result.put("checked", alerts.size());
        return result;
    }

    /**
     * 评估单个预警是否触发
     */
    private boolean evaluateAlert(StockAlert alert) {
        AlertType alertType;
        try {
            alertType = AlertType.valueOf(alert.getAlertType());
        } catch (IllegalArgumentException e) {
            log.warn("未知的预警类型: {}", alert.getAlertType());
            return false;
        }

        StockQuoteDTO quote = stockService.getQuote(alert.getStockCode());
        if (quote == null) {
            log.warn("无法获取股票行情: {}", alert.getStockCode());
            return false;
        }

        Double currentPrice = quote.getCurrentPrice() != null ? quote.getCurrentPrice().doubleValue() : null;
        if (currentPrice == null) {
            log.warn("无法获取当前价格: {}", alert.getStockCode());
            return false;
        }

        switch (alertType) {
            case PRICE_CROSS_ABOVE:
                return currentPrice >= alert.getThreshold();

            case PRICE_CROSS_BELOW:
                return currentPrice <= alert.getThreshold();

            case PRICE_CHANGE_PCT: {
                Double changePct = quote.getChangeRate() != null ? quote.getChangeRate().doubleValue() : null;
                if (changePct == null) return false;
                return Math.abs(changePct) >= alert.getThreshold();
            }

            case VOLUME_SPIKE: {
                if (quote.getVolume() == null) return false;
                return quote.getVolume() >= alert.getThreshold();
            }

            case RSI_OVERBOUGHT: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] rsiArr = technicalIndicatorService.rsi(closes, 14);
                if (rsiArr.length == 0) return false;
                double rsi = rsiArr[rsiArr.length - 1];
                return Double.isFinite(rsi) && rsi >= alert.getThreshold();
            }

            case RSI_OVERSOLD: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] rsiArr = technicalIndicatorService.rsi(closes, 14);
                if (rsiArr.length == 0) return false;
                double rsi = rsiArr[rsiArr.length - 1];
                return Double.isFinite(rsi) && rsi <= alert.getThreshold();
            }

            case MACD_GOLDEN_CROSS: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 60);
                double[] closes = toCloseArray(klines);
                List<double[]> macdResult = technicalIndicatorService.macd(closes, 12, 26, 9);
                if (macdResult.size() < 2) return false;
                double[] prev = macdResult.get(macdResult.size() - 2);
                double[] curr = macdResult.get(macdResult.size() - 1);
                return prev[0] <= prev[1] && curr[0] > curr[1];
            }

            case MACD_DEATH_CROSS: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 60);
                double[] closes = toCloseArray(klines);
                List<double[]> macdResult = technicalIndicatorService.macd(closes, 12, 26, 9);
                if (macdResult.size() < 2) return false;
                double[] prev = macdResult.get(macdResult.size() - 2);
                double[] curr = macdResult.get(macdResult.size() - 1);
                return prev[0] >= prev[1] && curr[0] < curr[1];
            }

            case MA_GOLDEN_CROSS: {
                int shortPeriod = alert.getThreshold() != null ? alert.getThreshold().intValue() : 5;
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] shortMa = technicalIndicatorService.sma(closes, shortPeriod);
                double[] longMa = technicalIndicatorService.sma(closes, 20);
                if (shortMa.length < 2 || longMa.length < 2) return false;
                int last = shortMa.length - 1;
                return shortMa[last - 1] <= longMa[last - 1] && shortMa[last] > longMa[last];
            }

            case MA_DEATH_CROSS: {
                int shortPeriod = alert.getThreshold() != null ? alert.getThreshold().intValue() : 5;
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] shortMa = technicalIndicatorService.sma(closes, shortPeriod);
                double[] longMa = technicalIndicatorService.sma(closes, 20);
                if (shortMa.length < 2 || longMa.length < 2) return false;
                int last = shortMa.length - 1;
                return shortMa[last - 1] >= longMa[last - 1] && shortMa[last] < longMa[last];
            }

            default:
                return false;
        }
    }

    /**
     * 获取预警对应的当前值
     */
    private Object getCurrentValue(StockAlert alert) {
        AlertType alertType;
        try {
            alertType = AlertType.valueOf(alert.getAlertType());
        } catch (IllegalArgumentException e) {
            return null;
        }

        StockQuoteDTO quote = stockService.getQuote(alert.getStockCode());

        switch (alertType) {
            case PRICE_CROSS_ABOVE:
            case PRICE_CROSS_BELOW:
                return quote != null && quote.getCurrentPrice() != null ? quote.getCurrentPrice().doubleValue() : null;
            case PRICE_CHANGE_PCT:
                return quote != null && quote.getChangeRate() != null ? quote.getChangeRate().doubleValue() : null;
            case VOLUME_SPIKE:
                return quote != null && quote.getVolume() != null ? quote.getVolume().doubleValue() : null;
            case RSI_OVERBOUGHT:
            case RSI_OVERSOLD: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] rsiArr = technicalIndicatorService.rsi(closes, 14);
                return rsiArr.length > 0 ? rsiArr[rsiArr.length - 1] : null;
            }
            case MACD_GOLDEN_CROSS:
            case MACD_DEATH_CROSS: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 60);
                double[] closes = toCloseArray(klines);
                List<double[]> macdResult = technicalIndicatorService.macd(closes, 12, 26, 9);
                return !macdResult.isEmpty() ? macdResult.get(macdResult.size() - 1)[0] : null;
            }
            case MA_GOLDEN_CROSS:
            case MA_DEATH_CROSS: {
                List<Map<String, Object>> klines = stockService.getKlineData(alert.getStockCode(), "", "", 30);
                double[] closes = toCloseArray(klines);
                double[] maArr = technicalIndicatorService.sma(closes, 20);
                return maArr.length > 0 ? maArr[maArr.length - 1] : null;
            }
            default:
                return null;
        }
    }

    /**
     * 从K线数据中提取收盘价数组
     */
    private double[] toCloseArray(List<Map<String, Object>> klines) {
        if (klines == null || klines.isEmpty()) return new double[0];
        double[] arr = new double[klines.size()];
        for (int i = 0; i < klines.size(); i++) {
            try {
                arr[i] = Double.parseDouble(klines.get(i).get("close").toString());
            } catch (Exception e) {
                arr[i] = 0.0;
            }
        }
        return arr;
    }
}
