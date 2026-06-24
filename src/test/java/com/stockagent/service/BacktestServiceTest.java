package com.stockagent.service;

import com.stockagent.common.BusinessException;
import com.stockagent.dto.BacktestResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BacktestService 单元测试
 * 测试双均线策略回测逻辑和参数校验
 */
@ExtendWith(MockitoExtension.class)
class BacktestServiceTest {

    @Mock
    private StockService stockService;

    private BacktestService backtestService;

    @BeforeEach
    void setUp() {
        backtestService = new BacktestService(stockService);
        ReflectionTestUtils.setField(backtestService, "httpTimeout", 10000);
    }

    @Nested
    @DisplayName("参数校验")
    class ParameterValidation {

        @Test
        @DisplayName("股票代码为空时应抛出异常")
        void shouldThrowWhenStockCodeBlank() {
            assertThrows(IllegalArgumentException.class,
                () -> backtestService.backtestMovingAverage("", 5, 20, "2024-01-01", "2024-12-31", BigDecimal.valueOf(100000)));
        }

        @Test
        @DisplayName("短期周期大于等于长期周期时应抛出异常")
        void shouldThrowWhenShortPeriodNotLessThanLong() {
            assertThrows(IllegalArgumentException.class,
                () -> backtestService.backtestMovingAverage("600519", 20, 5, "2024-01-01", "2024-12-31", BigDecimal.valueOf(100000)));
        }

        @Test
        @DisplayName("周期相等时应抛出异常")
        void shouldThrowWhenPeriodsEqual() {
            assertThrows(IllegalArgumentException.class,
                () -> backtestService.backtestMovingAverage("600519", 10, 10, "2024-01-01", "2024-12-31", BigDecimal.valueOf(100000)));
        }

        @Test
        @DisplayName("周期为零时应抛出异常")
        void shouldThrowWhenPeriodZero() {
            assertThrows(IllegalArgumentException.class,
                () -> backtestService.backtestMovingAverage("600519", 0, 20, "2024-01-01", "2024-12-31", BigDecimal.valueOf(100000)));
        }
    }

    @Nested
    @DisplayName("空数据处理")
    class EmptyDataHandling {

        @Test
        @DisplayName("无效股票代码应返回空结果")
        void shouldReturnEmptyResultForInvalidCode() {
            BacktestResultDTO result = backtestService.backtestMovingAverage(
                "INVALID", 5, 20, "2024-01-01", "2024-12-31", BigDecimal.valueOf(100000));
            assertNotNull(result);
            assertEquals("INVALID", result.getStockCode());
            assertEquals(BigDecimal.ZERO, result.getTotalReturn());
        }
    }
}
