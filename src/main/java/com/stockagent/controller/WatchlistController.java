package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping("/list")
    public ApiResponse<?> list() {
        return ApiResponse.ok(watchlistService.listWithQuotes());
    }

    @GetMapping("/groups")
    public ApiResponse<?> groups() {
        return ApiResponse.ok(watchlistService.listGroups());
    }

    @PostMapping("/add")
    public ApiResponse<?> add(@RequestBody Map<String, String> body) {
        watchlistService.add(
            body.get("stockCode"),
            body.get("stockName"),
            body.get("market"),
            body.get("groupName")
        );
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch-add")
    public ApiResponse<?> batchAdd(@RequestBody Map<String, String> body) {
        watchlistService.batchAdd(body.get("codes"), body.get("groupName"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/remove/{id}")
    public ApiResponse<?> remove(@PathVariable Long id) {
        watchlistService.remove(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/remove-by-code")
    public ApiResponse<?> removeByCode(@RequestParam String stockCode,
                                        @RequestParam(required = false) String groupName) {
        watchlistService.removeByCode(stockCode, groupName);
        return ApiResponse.ok(null);
    }

    @PostMapping("/move-group")
    public ApiResponse<?> moveGroup(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String groupName = body.get("groupName").toString();
        watchlistService.moveGroup(id, groupName);
        return ApiResponse.ok(null);
    }
}
