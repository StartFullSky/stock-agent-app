package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.service.NewsRadarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsRadarController {

    private final NewsRadarService newsRadarService;

    @GetMapping("/latest")
    public ApiResponse<?> latest(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(newsRadarService.getLatestNews(limit));
    }

    @GetMapping("/search")
    public ApiResponse<?> search(@RequestParam String keyword,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(newsRadarService.searchNews(keyword, page, limit));
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories() {
        return ApiResponse.ok(newsRadarService.getNewsByCategory());
    }

    @GetMapping("/stock")
    public ApiResponse<?> stockNews(@RequestParam String code,
                                     @RequestParam(required = false) String name) {
        return ApiResponse.ok(newsRadarService.getStockNews(code, name));
    }

    @GetMapping("/summary")
    public ApiResponse<?> summary() {
        return ApiResponse.ok(newsRadarService.generateNewsSummary());
    }
}
