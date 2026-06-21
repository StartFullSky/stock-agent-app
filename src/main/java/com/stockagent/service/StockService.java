package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.common.BusinessException;
import com.stockagent.dto.StockQuoteDTO;
import com.stockagent.dto.StockSearchDTO;
import com.stockagent.entity.StockInfo;
import com.stockagent.mapper.StockInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockInfoMapper stockInfoMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${cache.quote-ttl:10}")
    private long quoteTtl;

    @Value("${cache.kline-ttl:3600}")
    private long klineTtl;

    @Value("${cache.search-ttl:600}")
    private long searchTtl;

    private static final String TENCENT_API_URL = "http://qt.gtimg.cn/q=";
    private static final String SINA_KLINE_URL = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData";

    private static final String CACHE_PREFIX_QUOTE = "stock:quote:";
    private static final String CACHE_PREFIX_KLINE = "stock:kline:";
    private static final String CACHE_PREFIX_SEARCH = "stock:search:";

    private static final Map<String, String> INDEX_MAP = new LinkedHashMap<>();
    static {
        INDEX_MAP.put("上证指数", "sh000001");
        INDEX_MAP.put("深证成指", "sz399001");
        INDEX_MAP.put("创业板指", "sz399006");
        INDEX_MAP.put("沪深300", "sh000300");
        INDEX_MAP.put("上证50", "sh000016");
        INDEX_MAP.put("中证500", "sh000905");
    }

    public List<StockSearchDTO> searchStock(String keyword) {
        if (StrUtil.isBlank(keyword)) return new ArrayList<>();

        String cacheKey = CACHE_PREFIX_SEARCH + keyword;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSONUtil.toList(cached, StockSearchDTO.class);
        }

        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(StockInfo::getStockCode, keyword).or().like(StockInfo::getStockName, keyword))
               .eq(StockInfo::getStatus, 1).last("LIMIT 20");
        List<StockSearchDTO> result = stockInfoMapper.selectList(wrapper).stream()
                .map(this::convertToSearchDTO).collect(Collectors.toList());

        if (!result.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result), searchTtl, TimeUnit.SECONDS);
        }
        return result;
    }

    public StockQuoteDTO getQuote(String stockCode) {
        String indexCode = INDEX_MAP.get(stockCode);
        if (indexCode != null) stockCode = indexCode;

        String cacheKey = CACHE_PREFIX_QUOTE + stockCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return JSONUtil.toBean(cached, StockQuoteDTO.class);
        }

        try {
            StockQuoteDTO dto = fetchQuote(stockCode);
            if (dto != null && !"未知".equals(dto.getStockName())) {
                redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(dto), quoteTtl, TimeUnit.SECONDS);
                return dto;
            }
        } catch (Exception e) {
            log.error("获取行情失败: {}", e.getMessage());
        }
        return createEmptyQuote(stockCode);
    }

    public List<Map<String, Object>> getMainIndices() {
        String cacheKey = CACHE_PREFIX_QUOTE + "indices";
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return parseMapList(cached);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, String> entry : INDEX_MAP.entrySet()) {
            try {
                StockQuoteDTO quote = getQuote(entry.getValue());
                if (quote != null && !"未知".equals(quote.getStockName())) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("code", entry.getValue());
                    item.put("price", quote.getCurrentPrice());
                    item.put("change", quote.getChangeAmount());
                    item.put("changeRate", quote.getChangeRate());
                    results.add(item);
                }
            } catch (Exception ignored) {}
        }

        if (!results.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(results), quoteTtl, TimeUnit.SECONDS);
        }
        return results;
    }

    public List<Map<String, Object>> getKlineData(String stockCode, String startDate, String endDate, int limit) {
        String cacheKey = CACHE_PREFIX_KLINE + stockCode + ":" + startDate + ":" + endDate + ":" + limit;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return parseMapList(cached);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String sinaCode = toTencentCode(stockCode);
            if (sinaCode.startsWith("us") || sinaCode.startsWith("hk")) return results;

            int dataLen = Math.max(limit, 365);
            String url = SINA_KLINE_URL + "?symbol=" + sinaCode + "&scale=240&ma=no&datalen=" + dataLen;
            log.info("请求K线: {}", url);
            String response = HttpUtil.get(url, 10000);
            if (StrUtil.isBlank(response)) { log.warn("K线响应为空"); return results; }

            JSONArray arr = JSONUtil.parseArray(response);
            if (arr == null || arr.isEmpty()) { log.warn("K线JSON解析为空"); return results; }

            List<Map<String, Object>> allData = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Map<String, Object> kline = new LinkedHashMap<>();
                kline.put("date", obj.getStr("day"));
                kline.put("open", obj.getStr("open"));
                kline.put("close", obj.getStr("close"));
                kline.put("high", obj.getStr("high"));
                kline.put("low", obj.getStr("low"));
                kline.put("volume", obj.getStr("volume"));
                allData.add(kline);
            }

            // 按日期过滤
            List<Map<String, Object>> filtered = allData;
            if (StrUtil.isNotBlank(startDate)) {
                String finalStart = startDate;
                filtered = filtered.stream()
                    .filter(k -> k.get("date").toString().compareTo(finalStart) >= 0)
                    .collect(Collectors.toList());
            }
            if (StrUtil.isNotBlank(endDate)) {
                String finalEnd = endDate;
                filtered = filtered.stream()
                    .filter(k -> k.get("date").toString().compareTo(finalEnd) <= 0)
                    .collect(Collectors.toList());
            }

            // 取最近N条
            if (filtered.size() > limit) {
                results = new ArrayList<>(filtered.subList(filtered.size() - limit, filtered.size()));
            } else {
                results = new ArrayList<>(filtered);
            }

            if (!results.isEmpty()) {
                redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(results), klineTtl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("获取K线数据失败", e);
            throw new BusinessException(500, "获取K线数据失败: " + e.getMessage());
        }
        return results;
    }

    public List<Map<String, String>> searchGlobalStock(String keyword) {
        List<Map<String, String>> results = new ArrayList<>();
        List<StockSearchDTO> aStocks = searchStock(keyword);
        for (StockSearchDTO s : aStocks) {
            Map<String, String> item = new HashMap<>();
            item.put("code", s.getStockCode()); item.put("name", s.getStockName()); item.put("market", "A股");
            results.add(item);
        }
        if (keyword.matches("[a-zA-Z]+")) {
            String usCode = "us" + keyword.toUpperCase();
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + usCode, 3000);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + usCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]); item.put("name", parts[1]); item.put("market", "美股"); item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception e) { log.warn("美股搜索失败: {}", e.getMessage()); }
        }
        if (keyword.matches("\\d+")) {
            String hkCode = "hk" + String.format("%05d", Integer.parseInt(keyword));
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + hkCode, 3000);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + hkCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]); item.put("name", parts[1]); item.put("market", "港股"); item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception e) { log.warn("港股搜索失败: {}", e.getMessage()); }
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMapList(String json) {
        try {
            List<JSONObject> list = JSONUtil.toList(json, JSONObject.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (JSONObject obj : list) {
                result.add(new LinkedHashMap<>(obj));
            }
            return result;
        } catch (Exception e) {
            log.warn("缓存解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private StockQuoteDTO fetchQuote(String stockCode) {
        String tencentCode = toTencentCode(stockCode);
        String response = HttpUtil.get(TENCENT_API_URL + tencentCode, 5000);
        if (StrUtil.isBlank(response)) return null;
        List<StockQuoteDTO> results = parseTencentResponse(response, List.of(stockCode));
        return results.isEmpty() ? null : results.get(0);
    }

    private String toTencentCode(String stockCode) {
        if (stockCode == null) return "";
        String code = stockCode.trim();
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("hk") || code.startsWith("us")) return code;
        if (code.startsWith("SH") || code.startsWith("SZ") || code.startsWith("HK") || code.startsWith("US")) return code.toLowerCase();
        if (code.matches("[A-Z]{1,5}")) return "us" + code;
        if (code.matches("\\d{5}")) return "hk" + code;
        if (code.startsWith("6")) return "sh" + code;
        if (code.startsWith("0") || code.startsWith("3")) return "sz" + code;
        if (code.startsWith("8") || code.startsWith("4")) return "bj" + code;
        return "sh" + code;
    }

    private List<StockQuoteDTO> parseTencentResponse(String response, List<String> stockCodes) {
        List<StockQuoteDTO> results = new ArrayList<>();
        String[] lines = response.split("\n");
        for (String line : lines) {
            if (StrUtil.isBlank(line) || !line.contains("~")) continue;
            try {
                String[] p = line.split("~");
                if (p.length < 4) continue;
                StockQuoteDTO dto = new StockQuoteDTO();
                dto.setStockCode(p[2]); dto.setStockName(p[1]);
                dto.setCurrentPrice(parseBD(p[3])); dto.setPreClose(parseBD(p[4])); dto.setOpenPrice(parseBD(p[5]));
                if (p.length > 6) dto.setVolume(parseLong(p[6]));
                if (p.length > 31) dto.setChangeAmount(parseBD(p[31]));
                if (p.length > 32) dto.setChangeRate(parseBD(p[32]));
                if (p.length > 33) dto.setHighPrice(parseBD(p[33]));
                if (p.length > 34) dto.setLowPrice(parseBD(p[34]));
                if (p.length > 37) dto.setAmount(parseBD(p[37]));
                results.add(dto);
            } catch (Exception e) { log.warn("解析行情失败: {}", e.getMessage()); }
        }
        return results;
    }

    private BigDecimal parseBD(String s) { try { return new BigDecimal(s.trim()); } catch (Exception e) { return BigDecimal.ZERO; } }
    private Long parseLong(String s) { try { return Long.parseLong(s.trim().split("\\.")[0]); } catch (Exception e) { return 0L; } }
    private StockQuoteDTO createEmptyQuote(String code) { StockQuoteDTO dto = new StockQuoteDTO(); dto.setStockCode(code); dto.setStockName("未知"); dto.setCurrentPrice(BigDecimal.ZERO); dto.setChangeRate(BigDecimal.ZERO); return dto; }
    private StockSearchDTO convertToSearchDTO(StockInfo e) { StockSearchDTO dto = new StockSearchDTO(); dto.setStockCode(e.getStockCode()); dto.setStockName(e.getStockName()); dto.setMarket(e.getMarket()); dto.setIndustry(e.getIndustry()); return dto; }
}