package com.stockagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.dto.StockQuoteDTO;
import com.stockagent.entity.UserFavorite;
import com.stockagent.mapper.UserFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final StockService stockService;

    public List<Map<String, Object>> getFavorites(Long userId) {
        List<UserFavorite> list = favoriteMapper.selectList(
            new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByAsc(UserFavorite::getSortOrder)
        );
        
        // Get real-time quotes for each favorite
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFavorite fav : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fav.getId());
            item.put("stockCode", fav.getStockCode());
            item.put("stockName", fav.getStockName());
            item.put("market", fav.getMarket());
            
            // Get real-time quote
            try {
                StockQuoteDTO quote = stockService.getQuote(fav.getStockCode());
                if (quote != null && !"未知".equals(quote.getStockName())) {
                    item.put("price", quote.getCurrentPrice());
                    item.put("changeRate", quote.getChangeRate());
                    item.put("changeAmount", quote.getChangeAmount());
                    // Update stock name if empty
                    if (fav.getStockName() == null || fav.getStockName().isEmpty()) {
                        fav.setStockName(quote.getStockName());
                        favoriteMapper.updateById(fav);
                    }
                }
            } catch (Exception e) {
                log.warn("Get quote failed for {}: {}", fav.getStockCode(), e.getMessage());
            }
            result.add(item);
        }
        return result;
    }

    public boolean addFavorite(Long userId, String stockCode) {
        // Check if already exists
        Long count = favoriteMapper.selectCount(
            new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getStockCode, stockCode)
        );
        if (count > 0) return true; // Already exists

        // Get stock info
        String stockName = "";
        String market = "A";
        try {
            StockQuoteDTO quote = stockService.getQuote(stockCode);
            if (quote != null && !"未知".equals(quote.getStockName())) {
                stockName = quote.getStockName();
            }
            if (stockCode.matches("[A-Z]{1,5}")) market = "US";
            else if (stockCode.matches("\\d{5}")) market = "HK";
        } catch (Exception e) {
            log.warn("Get stock info failed: {}", e.getMessage());
        }

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId);
        fav.setStockCode(stockCode);
        fav.setStockName(stockName);
        fav.setMarket(market);
        fav.setSortOrder(0);
        fav.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(fav);
        return true;
    }

    public boolean removeFavorite(Long userId, String stockCode) {
        int deleted = favoriteMapper.delete(
            new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getStockCode, stockCode)
        );
        return deleted > 0;
    }
}
