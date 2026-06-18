package com.stockagent.controller;

import com.stockagent.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Tag(name = "Favorites API")
@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @Operation(summary = "Get favorites list with real-time quotes")
    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> list = favoriteService.getFavorites(0L);
            result.put("code", 200);
            result.put("data", list);
            result.put("success", true);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @Operation(summary = "Add stock to favorites")
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String stockCode = request.get("stockCode");
            if (stockCode == null || stockCode.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "Stock code is required");
                result.put("success", false);
                return result;
            }
            boolean success = favoriteService.addFavorite(0L, stockCode.trim());
            result.put("code", success ? 200 : 500);
            result.put("success", success);
            if (!success) result.put("message", "Failed to add favorite");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("success", false);
        }
        return result;
    }

    @Operation(summary = "Remove stock from favorites")
    @DeleteMapping("/remove")
    public Map<String, Object> remove(@RequestParam String code) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = favoriteService.removeFavorite(0L, code);
            result.put("code", success ? 200 : 404);
            result.put("success", success);
            if (!success) result.put("message", "Favorite not found");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
            result.put("success", false);
        }
        return result;
    }
}
