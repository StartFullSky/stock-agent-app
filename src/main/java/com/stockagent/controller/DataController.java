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
    private final SpecialDataService specialDataService;
    private final HithinkFinanceService hithinkService;

    public DataController(StockService s, WebSearchService w, TradeReviewService t, BacktestService b,
                          SpecialDataService specialDataService, HithinkFinanceService hithinkService) {
        this.stockService = s; this.webSearchService = w;
        this.tradeReviewService = t; this.backtestService = b;
        this.specialDataService = specialDataService;
        this.hithinkService = hithinkService;
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

    @Operation(summary = "估值快照(PE/PB/PS/PCF)")
    @GetMapping("/stock/valuation/{code}")
    public ApiResponse<Map<String, Object>> valuation(@PathVariable String code) {
        HithinkFinanceService.TickerInfo t = hithinkService.resolveTicker(code, null);
        if (t == null) return ApiResponse.ok(new LinkedHashMap<>());
        return ApiResponse.ok(specialDataService.valuation(t.thscode));
    }

    @Operation(summary = "财务指标(成长/盈利/偿债/营运/现金流)，report如2025-3")
    @GetMapping("/stock/financial-indicators")
    public ApiResponse<Map<String, Object>> financialIndicators(@RequestParam String code, @RequestParam String report) {
        HithinkFinanceService.TickerInfo t = hithinkService.resolveTicker(code, null);
        if (t == null) return ApiResponse.ok(new LinkedHashMap<>());
        return ApiResponse.ok(specialDataService.financialIndicators(t.thscode, report));
    }

    @Operation(summary = "涨停股票池")
    @GetMapping("/market/limit-up")
    public ApiResponse<Map<String, Object>> limitUp(@RequestParam(required = false) String date,
                                                    @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(specialDataService.limitUpPool(date, size));
    }

    @Operation(summary = "跌停股票池")
    @GetMapping("/market/limit-down")
    public ApiResponse<Map<String, Object>> limitDown(@RequestParam(required = false) String date,
                                                      @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(specialDataService.limitDownPool(date, size));
    }

    @Operation(summary = "炸板股票池")
    @GetMapping("/market/limit-break")
    public ApiResponse<Map<String, Object>> limitBreak(@RequestParam(required = false) String date,
                                                       @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(specialDataService.limitBreakPool(date, size));
    }

    @Operation(summary = "连板天梯(近30交易日)")
    @GetMapping("/market/limit-up-ladder")
    public ApiResponse<Map<String, Object>> limitUpLadder() {
        return ApiResponse.ok(specialDataService.limitUpLadder());
    }

    @Operation(summary = "热股榜/飙升榜 Top30")
    @GetMapping("/market/hot")
    public ApiResponse<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "day") String period,
                                                      @RequestParam(defaultValue = "false") boolean skyrocket) {
        return ApiResponse.ok(specialDataService.hotStockList(period, skyrocket));
    }

    @Operation(summary = "龙虎榜")
    @GetMapping("/market/dragon-tiger")
    public ApiResponse<Map<String, Object>> dragonTiger(@RequestParam(required = false) String date,
                                                        @RequestParam(defaultValue = "all") String boardType) {
        return ApiResponse.ok(specialDataService.dragonTiger(date, boardType));
    }

    @Operation(summary = "个股异动原因")
    @GetMapping("/market/anomaly")
    public ApiResponse<List<Map<String, Object>>> anomaly(@RequestParam String code) {
        HithinkFinanceService.TickerInfo t = hithinkService.resolveTicker(code, null);
        if (t == null) return ApiResponse.ok(new ArrayList<>());
        return ApiResponse.ok(specialDataService.anomalyByStock(t.thscode));
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