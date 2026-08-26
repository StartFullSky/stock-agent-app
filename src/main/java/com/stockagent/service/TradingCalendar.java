package com.stockagent.service;

import com.stockagent.enums.MarketPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;

/**
 * A股交易日历服务
 * <p>
 * 注：当前仅判断周一至周五为交易日，未接入节假日API。
 * 如需精确判断，可接入第三方节假日接口（如支付宝/腾讯节假日API）。
 */
@Slf4j
@Service
public class TradingCalendar {

    /**
     * 判断今天是否为交易日（周一至周五）
     */
    public boolean isTradingDay() {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        // 周一至周五为交易日（需注意：实际还需排除法定节假日）
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 获取当前市场阶段
     */
    public MarketPhase getCurrentPhase() {
        return MarketPhase.fromNow();
    }

    /**
     * 获取人可读的市场状态（中文）
     */
    public String getMarketStatus() {
        if (!isTradingDay()) {
            LocalDate today = LocalDate.now();
            if (today.getDayOfWeek() == DayOfWeek.SATURDAY) {
                return "周六休市";
            } else {
                return "周日休市";
            }
        }

        MarketPhase phase = getCurrentPhase();
        return phase.getLabel();
    }

    /**
     * 获取完整的市场信息
     */
    public Map<String, Object> getMarketInfo() {
        Map<String, Object> info = new HashMap<>();
        boolean tradingDay = isTradingDay();
        MarketPhase phase = getCurrentPhase();

        info.put("isTradingDay", tradingDay);
        info.put("phase", phase.name());
        info.put("phaseLabel", phase.getLabel());
        info.put("now", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 交易时段信息
        Map<String, String> tradingHours = new HashMap<>();
        tradingHours.put("morning", "09:30 - 11:30");
        tradingHours.put("afternoon", "13:00 - 15:00");
        tradingHours.put("total", "09:30 - 11:30, 13:00 - 15:00");
        info.put("tradingHours", tradingHours);

        // 下次开盘时间
        info.put("nextOpen", getNextOpenTime());

        // 当前阶段时间段
        info.put("phaseOpenTime", phase.getOpenTime().toString());
        info.put("phaseCloseTime", phase.getCloseTime().toString());

        return info;
    }

    /**
     * 计算下次开盘时间
     */
    private String getNextOpenTime() {
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        if (isTradingDay()) {
            if (now.isBefore(marketOpen)) {
                // 今天是交易日且还没开盘，下次开盘就是今天
                return today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        + " 09:30";
            } else {
                // 今天已开盘或已收盘，找下一个交易日
                LocalDate nextTradingDay = findNextTradingDay(today);
                return nextTradingDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        + " 09:30";
            }
        } else {
            // 今天不是交易日，找下一个交易日
            LocalDate nextTradingDay = findNextTradingDay(today);
            return nextTradingDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    + " 09:30";
        }
    }

    /**
     * 找到下一个交易日（跳过周末）
     */
    private LocalDate findNextTradingDay(LocalDate from) {
        LocalDate next = from.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY
                || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }
}
