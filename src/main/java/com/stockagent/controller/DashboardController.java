package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/indices")
    public ApiResponse<?> getIndices() {
        return ApiResponse.ok(dashboardService.getIndices());
    }

    @GetMapping("/rankings")
    public ApiResponse<?> getRankings() {
        return ApiResponse.ok(dashboardService.getRankings());
    }

    @GetMapping("/news")
    public ApiResponse<?> getNews() {
        return ApiResponse.ok(dashboardService.getNews());
    }

    @GetMapping("/summary")
    public ApiResponse<?> getSummary() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("indices", dashboardService.getIndices());
        result.put("rankings", dashboardService.getRankings());
        result.put("news", dashboardService.getNews());
        result.put("summaryText", dashboardService.generateSummaryText());
        return ApiResponse.ok(result);
    }
}
