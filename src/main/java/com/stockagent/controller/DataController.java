package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.dto.*;
import com.stockagent.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Tag(name = "数据接口")
@RestController
@RequestMapping("/api")
public class DataController {
    private final StockService stockService;
    private final WebSearchService webSearchService;
    private final TradeReviewService tradeReviewService;
    private final BacktestService backtestService;

    public DataController(StockService s, WebSearchService w, TradeReviewService t, BacktestService b) {
        this.stockService = s; this.webSearchService = w;
        this.tradeReviewService = t; this.backtestService = b;
    }

    @Operation(summary = "搜索A股")
    @GetMapping("/stock/search")
    public ApiResponse<List<StockSearchDTO>> search(@RequestParam String keyword) {
        return ApiResponse.ok(stockService.searchStock(keyword));
    }

    @Operation(summary = "搜索全球股票(A股/美股/港股/指数)")
    @GetMapping("/stock/global-search")
    public ApiResponse<List<Map<String, String>>> globalSearch(@RequestParam String keyword) {
        return ApiResponse.ok(stockService.searchGlobalStock(keyword));
    }

    @Operation(summary = "实时行情(A股/美股/港股)")
    @GetMapping("/stock/quote/{code}")
    public ApiResponse<StockQuoteDTO> quote(@PathVariable String code) {
        return ApiResponse.ok(stockService.getQuote(code));
    }

    @Operation(summary = "主要指数行情")
    @GetMapping("/stock/indices")
    public ApiResponse<List<Map<String, Object>>> indices() {
        return ApiResponse.ok(stockService.getMainIndices());
    }

    @Operation(summary = "历史K线数据")
    @GetMapping("/stock/kline")
    public ApiResponse<List<Map<String, Object>>> kline(
            @RequestParam String code, @RequestParam String startDate,
            @RequestParam String endDate, @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(stockService.getKlineData(code, startDate, endDate, limit));
    }

    @Operation(summary = "Web搜索")
    @GetMapping("/search")
    public ApiResponse<List<Map<String, String>>> webSearch(@RequestParam String query, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(webSearchService.search(query, Math.min(limit, 20)));
    }

    @Operation(summary = "交易复盘")
    @GetMapping("/trade-review")
    public ApiResponse<TradeReviewDTO> tradeReview(@RequestParam Long userId) {
        return ApiResponse.ok(tradeReviewService.analyzeTradeBehavior(userId));
    }

    @Operation(summary = "双均线回测")
    @GetMapping("/backtest/ma")
    public ApiResponse<BacktestResultDTO> backtestMA(
            @RequestParam String stockCode, @RequestParam(defaultValue = "5") int sp,
            @RequestParam(defaultValue = "20") int lp, @RequestParam String startDate, @RequestParam String endDate) {
        return ApiResponse.ok(backtestService.backtestMovingAverage(stockCode, sp, lp, startDate, endDate, BigDecimal.valueOf(100000)));
    }
}