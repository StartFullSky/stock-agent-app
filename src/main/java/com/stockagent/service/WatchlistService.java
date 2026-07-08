package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.entity.Watchlist;
import com.stockagent.mapper.WatchlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistMapper watchlistMapper;

    private static final String TENCENT_API_URL = "http://qt.gtimg.cn/q=";

    public List<Watchlist> listAll() {
        return watchlistMapper.selectList(
            new LambdaQueryWrapper<Watchlist>()
                .orderByAsc(Watchlist::getGroupName, Watchlist::getSortOrder)
        );
    }

    public List<Watchlist> listByGroup(String groupName) {
        return watchlistMapper.selectList(
            new LambdaQueryWrapper<Watchlist>()
                .eq(Watchlist::getGroupName, groupName)
                .orderByAsc(Watchlist::getSortOrder)
        );
    }

    public List<String> listGroups() {
        List<Watchlist> all = listAll();
        return all.stream()
            .map(Watchlist::getGroupName)
            .distinct()
            .collect(Collectors.toList());
    }

    public void add(String stockCode, String stockName, String market, String groupName) {
        if (StrUtil.isBlank(stockCode)) {
            throw new IllegalArgumentException("Stock code required");
        }
        if (StrUtil.isBlank(groupName)) groupName = "默认分组";
        if (StrUtil.isBlank(market)) market = "A";

        // Check duplicate
        Long count = watchlistMapper.selectCount(
            new LambdaQueryWrapper<Watchlist>()
                .eq(Watchlist::getStockCode, stockCode)
                .eq(Watchlist::getGroupName, groupName)
        );
        if (count > 0) {
            throw new IllegalArgumentException("Already in watchlist");
        }

        Watchlist item = new Watchlist();
        item.setStockCode(stockCode.toUpperCase());
        item.setStockName(stockName);
        item.setMarket(market);
        item.setGroupName(groupName);
        item.setSortOrder(0);
        watchlistMapper.insert(item);
    }

    public void batchAdd(String codes, String groupName) {
        if (StrUtil.isBlank(codes)) return;
        String[] codeArr = codes.split("[,，\\s\n]+");
        for (String code : codeArr) {
            code = code.trim();
            if (StrUtil.isBlank(code)) continue;
            try {
                add(code, null, null, groupName);
            } catch (IllegalArgumentException e) {
                log.warn("Skip {}: {}", code, e.getMessage());
            }
        }
    }

    public void remove(Long id) {
        watchlistMapper.deleteById(id);
    }

    public void removeByCode(String stockCode, String groupName) {
        LambdaQueryWrapper<Watchlist> wrapper = new LambdaQueryWrapper<Watchlist>()
            .eq(Watchlist::getStockCode, stockCode.toUpperCase());
        if (StrUtil.isNotBlank(groupName)) {
            wrapper.eq(Watchlist::getGroupName, groupName);
        }
        watchlistMapper.delete(wrapper);
    }

    public void moveGroup(Long id, String newGroup) {
        Watchlist item = watchlistMapper.selectById(id);
        if (item != null) {
            item.setGroupName(newGroup);
            watchlistMapper.updateById(item);
        }
    }

    /**
     * Fetch real-time quotes for all watchlist items
     */
    public List<Map<String, Object>> listWithQuotes() {
        List<Watchlist> items = listAll();
        if (items.isEmpty()) return new ArrayList<>();

        // Group by market
        Map<String, List<Watchlist>> byMarket = items.stream()
            .collect(Collectors.groupingBy(w -> w.getMarket() == null ? "A" : w.getMarket()));

        List<Map<String, Object>> result = new ArrayList<>();

        // Fetch A-share quotes
        List<Watchlist> aItems = byMarket.getOrDefault("A", new ArrayList<>());
        if (!aItems.isEmpty()) {
            String codes = aItems.stream()
                .map(w -> "s_" + w.getStockCode().toLowerCase())
                .collect(Collectors.joining(","));
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + codes, 5000);
                Map<String, String> quoteMap = parseTencentQuotes(resp);
                for (Watchlist w : aItems) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", w.getId());
                    item.put("stockCode", w.getStockCode());
                    item.put("stockName", w.getStockName());
                    item.put("market", w.getMarket());
                    item.put("groupName", w.getGroupName());
                    String quoteKey = "s_" + w.getStockCode().toLowerCase();
                    String quote = quoteMap.get(quoteKey);
                    if (quote != null) {
                        String[] parts = quote.split("~");
                        if (parts.length >= 4) {
                            item.put("price", parts[1]);
                            item.put("changeRate", parts.length > 3 ? parts[3] : "0");
                        }
                    }
                    result.add(item);
                }
            } catch (Exception e) {
                log.error("Fetch quotes failed", e);
                for (Watchlist w : aItems) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", w.getId());
                    item.put("stockCode", w.getStockCode());
                    item.put("stockName", w.getStockName());
                    item.put("market", w.getMarket());
                    item.put("groupName", w.getGroupName());
                    result.add(item);
                }
            }
        }

        // HK/US - just return without real-time quote for now
        for (String market : Arrays.asList("HK", "US")) {
            List<Watchlist> mItems = byMarket.getOrDefault(market, new ArrayList<>());
            for (Watchlist w : mItems) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", w.getId());
                item.put("stockCode", w.getStockCode());
                item.put("stockName", w.getStockName());
                item.put("market", w.getMarket());
                item.put("groupName", w.getGroupName());
                result.add(item);
            }
        }

        return result;
    }

    private Map<String, String> parseTencentQuotes(String resp) {
        Map<String, String> map = new HashMap<>();
        if (StrUtil.isBlank(resp)) return map;
        String[] lines = resp.split(";");
        for (String line : lines) {
            line = line.trim();
            if (!line.contains("=")) continue;
            int eq = line.indexOf('=');
            String key = line.substring(0, eq).trim().replace("v_", "");
            String val = line.substring(eq + 1).trim().replace("\"", "");
            if (!val.isEmpty()) {
                map.put(key, val);
            }
        }
        return map;
    }
}
