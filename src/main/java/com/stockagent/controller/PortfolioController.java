package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping("/open")
    public ApiResponse<?> listOpen() {
        return ApiResponse.ok(portfolioService.listOpen());
    }

    @GetMapping("/closed")
    public ApiResponse<?> listClosed() {
        return ApiResponse.ok(portfolioService.listClosed());
    }

    @GetMapping("/summary")
    public ApiResponse<?> summary() {
        return ApiResponse.ok(portfolioService.getSummary());
    }

    @GetMapping("/trades/{portfolioId}")
    public ApiResponse<?> trades(@PathVariable Long portfolioId) {
        return ApiResponse.ok(portfolioService.getTrades(portfolioId));
    }

    @PostMapping("/create")
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        portfolioService.create(
            (String) body.get("stockCode"),
            (String) body.get("stockName"),
            (String) body.get("market"),
            new BigDecimal(body.get("price").toString()),
            Integer.parseInt(body.get("quantity").toString()),
            body.get("tradeDate") != null ? LocalDate.parse(body.get("tradeDate").toString()) : null,
            body.get("fee") != null ? new BigDecimal(body.get("fee").toString()) : null
        );
        return ApiResponse.ok(null);
    }

    @PostMapping("/add-trade")
    public ApiResponse<?> addTrade(@RequestBody Map<String, Object> body) {
        portfolioService.addTrade(
            Long.valueOf(body.get("portfolioId").toString()),
            (String) body.get("tradeType"),
            new BigDecimal(body.get("price").toString()),
            Integer.parseInt(body.get("quantity").toString()),
            body.get("tradeDate") != null ? LocalDate.parse(body.get("tradeDate").toString()) : null,
            body.get("fee") != null ? new BigDecimal(body.get("fee").toString()) : null,
            (String) body.get("note")
        );
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        portfolioService.delete(id);
        return ApiResponse.ok(null);
    }
}
