package com.stockagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.dto.StockQuoteDTO;
import com.stockagent.entity.UserFavorite;
import com.stockagent.mapper.UserFavoriteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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

        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFavorite fav : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", fav.getId());
            item.put("stockCode", fav.getStockCode());
            item.put("stockName", fav.getStockName());
            item.put("market", fav.getMarket());

            try {
                StockQuoteDTO quote = stockService.getQuote(fav.getStockCode());
                if (quote != null && !"未知".equals(quote.getStockName())) {
                    item.put("price", quote.getCurrentPrice());
                    item.put("changeRate", quote.getChangeRate());
                    item.put("changeAmount", quote.getChangeAmount());
                    if (fav.getStockName() == null || fav.getStockName().isEmpty()) {
                        fav.setStockName(quote.getStockName());
                        favoriteMapper.updateById(fav);
                    }
                }
            } catch (Exception e) {
                log.warn("获取行情失败 {}", fav.getStockCode(), e);
                item.put("price", "--");
                item.put("changeRate", "--");
                item.put("changeAmount", "--");
            }
            result.add(item);
        }
        return result;
    }

    public boolean addFavorite(Long userId, String stockCode) {
        Long count = favoriteMapper.selectCount(
            new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getStockCode, stockCode)
        );
        if (count > 0) return true;

        String stockName = ""; String market = "A";
        try {
            StockQuoteDTO quote = stockService.getQuote(stockCode);
            if (quote != null && !"未知".equals(quote.getStockName())) stockName = quote.getStockName();
            if (stockCode.matches("[A-Z]{1,5}")) market = "US";
            else if (stockCode.matches("\\d{5}")) market = "HK";
        } catch (Exception e) { log.warn("获取股票信息失败", e); }

        UserFavorite fav = new UserFavorite();
        fav.setUserId(userId); fav.setStockCode(stockCode); fav.setStockName(stockName);
        fav.setMarket(market); fav.setSortOrder(0); fav.setCreatedAt(LocalDateTime.now());
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