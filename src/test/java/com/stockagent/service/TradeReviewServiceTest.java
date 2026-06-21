package com.stockagent.service;

import com.stockagent.dto.TradeReviewDTO;
import com.stockagent.entity.StockTrade;
import com.stockagent.mapper.StockTradeMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TradeReviewService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TradeReviewServiceTest {

    @Mock
    private StockTradeMapper stockTradeMapper;

    private TradeReviewService tradeReviewService;

    @BeforeEach
    void setUp() {
        tradeReviewService = new TradeReviewService(stockTradeMapper);
    }

    @Nested
    @DisplayName("无交易记录")
    class NoTrades {

        @Test
        @DisplayName("无交易时应返回空结果")
        void shouldReturnEmptyWhenNoTrades() {
            when(stockTradeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

            TradeReviewDTO result = tradeReviewService.analyzeTradeBehavior(1L);

            assertNotNull(result);
            assertEquals(0, result.getTotalTrades());
            assertEquals(BigDecimal.ZERO, result.getTotalProfit());
            assertEquals(BigDecimal.ZERO, result.getWinRate());
        }
    }

    @Nested
    @DisplayName("交易分析")
    class TradeAnalysis {

        @Test
        @DisplayName("应正确计算买卖次数和盈亏")
        void shouldCalculateCorrectTradeStats() {
            StockTrade buy1 = createTrade(1L, "600519", "贵州茅台", 1, 100,
                BigDecimal.valueOf(1800), BigDecimal.valueOf(180000), LocalDate.of(2024, 1, 10));
            StockTrade sell1 = createTrade(1L, "600519", "贵州茅台", 2, 100,
                BigDecimal.valueOf(1900), BigDecimal.valueOf(190000), LocalDate.of(2024, 2, 10));
            StockTrade buy2 = createTrade(1L, "000858", "五粮液", 1, 200,
                BigDecimal.valueOf(150), BigDecimal.valueOf(30000), LocalDate.of(2024, 1, 15));

            when(stockTradeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(sell1, buy1, buy2));

            TradeReviewDTO result = tradeReviewService.analyzeTradeBehavior(1L);

            assertNotNull(result);
            assertEquals(3, result.getTotalTrades());
            assertEquals(2, result.getBuyCount());
            assertEquals(1, result.getSellCount());
            assertEquals(2, result.getUniqueStocks());
        }

        @Test
        @DisplayName("应正确计算胜率")
        void shouldCalculateWinRate() {
            // 盈利交易
            StockTrade buy1 = createTrade(1L, "600519", "贵州茅台", 1, 100,
                BigDecimal.valueOf(1800), BigDecimal.valueOf(180000), LocalDate.of(2024, 1, 10));
            StockTrade sell1 = createTrade(1L, "600519", "贵州茅台", 2, 100,
                BigDecimal.valueOf(1900), BigDecimal.valueOf(190000), LocalDate.of(2024, 2, 10));

            when(stockTradeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(sell1, buy1));

            TradeReviewDTO result = tradeReviewService.analyzeTradeBehavior(1L);

            assertNotNull(result.getWinRate());
            assertTrue(result.getWinRate().compareTo(BigDecimal.ZERO) > 0);
        }
    }

    private StockTrade createTrade(Long userId, String code, String name, int type,
                                   int qty, BigDecimal price, BigDecimal amount, LocalDate date) {
        StockTrade trade = new StockTrade();
        trade.setUserId(userId);
        trade.setStockCode(code);
        trade.setStockName(name);
        trade.setTradeType(type);
        trade.setQuantity(qty);
        trade.setPrice(price);
        trade.setAmount(amount);
        trade.setTradeDate(date);
        return trade;
    }
}
