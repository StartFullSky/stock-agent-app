package com.stockagent.controller;

import com.stockagent.common.ApiResponse;
import com.stockagent.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "收藏接口")
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "获取自选股列表（含实时行情）")
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(favoriteService.getFavorites(0L));
    }

    @Operation(summary = "添加自选股")
    @PostMapping("/add")
    public ApiResponse<String> add(@RequestBody Map<String, String> request) {
        String stockCode = request.get("stockCode");
        if (stockCode == null || stockCode.trim().isEmpty()) {
            return ApiResponse.fail(400, "股票代码不能为空");
        }
        favoriteService.addFavorite(0L, stockCode.trim());
        return ApiResponse.ok("添加成功");
    }

    @Operation(summary = "删除自选股")
    @DeleteMapping("/remove")
    public ApiResponse<String> remove(@RequestParam String code) {
        boolean success = favoriteService.removeFavorite(0L, code);
        if (!success) return ApiResponse.fail(404, "未找到该自选股");
        return ApiResponse.ok("删除成功");
    }
}