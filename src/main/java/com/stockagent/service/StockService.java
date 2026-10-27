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
    private final HithinkFinanceService hithink;

    @Value("${cache.quote-ttl:10}")
    private long quoteTtl;

    @Value("${cache.kline-ttl:3600}")
    private long klineTtl;

    @Value("${cache.search-ttl:600}")
    private long searchTtl;

    @Value("${http.timeout.quote:5000}")
    private int httpTimeoutQuote;

    @Value("${http.timeout.kline:10000}")
    private int httpTimeoutKline;

    @Value("${http.timeout.global-search:3000}")
    private int httpTimeoutGlobalSearch;

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

        // 主数据源：同花顺标的检索（准确消歧）
        try {
            List<Map<String, String>> tickers = hithink.searchTickers(keyword, "a-share", 20);
            if (!tickers.isEmpty()) {
                List<StockSearchDTO> result = tickers.stream().map(r -> {
                    StockSearchDTO dto = new StockSearchDTO();
                    dto.setStockCode(r.get("code"));
                    dto.setStockName(r.get("name"));
                    dto.setMarket("A股");
                    return dto;
                }).collect(Collectors.toList());
                redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result), searchTtl, TimeUnit.SECONDS);
                return result;
            }
        } catch (Exception e) {
            log.warn("同花顺搜索失败，回退本地代码表: {}", e.getMessage());
        }

        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(StockInfo::getStockCode, keyword).or().like(StockInfo::getStockName, keyword))
               .eq(StockInfo::getStatus, 1).last("LIMIT 20");
        List<StockSearchDTO> result = stockInfoMapper.selectList(wrapper).stream()
                .map(this::convertToSearchDTO).collect(Collectors.toList());

        // 缓存结果（包括空结果），防止缓存击穿；空结果使用较短TTL
        long ttl = result.isEmpty() ? Math.min(searchTtl, 60) : searchTtl;
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(result), ttl, TimeUnit.SECONDS);
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
            // 主数据源：同花顺行情快照（A股/指数）；美股/港股不支持时回退腾讯
            StockQuoteDTO dto = fetchQuoteHithink(stockCode);
            if (dto == null || "未知".equals(dto.getStockName())) {
                dto = fetchQuote(stockCode);
            }
            if (dto != null && !"未知".equals(dto.getStockName())) {
                redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(dto), quoteTtl, TimeUnit.SECONDS);
                return dto;
            }
        } catch (Exception e) {
            log.error("获取行情失败", e);
        }
        return createEmptyQuote(stockCode);
    }

    public List<Map<String, Object>> getMainIndices() {
        String cacheKey = CACHE_PREFIX_QUOTE + "indices";
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return parseMapList(cached);
        }

        // 主数据源：同花顺指数行情快照（一次批量）
        List<Map<String, Object>> results = fetchMainIndicesHithink();

        if (results.isEmpty()) {
            results = fetchMainIndicesTencent();
        }

        if (!results.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(results), quoteTtl, TimeUnit.SECONDS);
        }
        return results;
    }

    private List<Map<String, Object>> fetchMainIndicesTencent() {
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
            } catch (Exception e) { log.warn("获取指数行情失败 {}", entry.getKey(), e); }
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

            // 主数据源：同花顺历史K线（默认前复权）
            List<Map<String, Object>> allData = fetchKlineHithink(stockCode, startDate, endDate, limit);
            if (allData.isEmpty()) {
                allData = fetchKlineSina(sinaCode, limit);
            }
            if (allData.isEmpty()) { log.warn("K线数据为空"); return results; }

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
                String resp = HttpUtil.get(TENCENT_API_URL + usCode, httpTimeoutGlobalSearch);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + usCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]); item.put("name", parts[1]); item.put("market", "美股"); item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception e) { log.warn("美股搜索失败", e); }
        }
        if (keyword.matches("\\d+")) {
            String hkCode = "hk" + String.format("%05d", Integer.parseInt(keyword));
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + hkCode, httpTimeoutGlobalSearch);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + hkCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]); item.put("name", parts[1]); item.put("market", "港股"); item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception e) { log.warn("港股搜索失败", e); }
        }
        return results;
    }

    /** 是否美股/港股代码（同花顺公开接口不支持，走腾讯兜底） */
    private boolean isUsHkCode(String code) {
        if (code == null) return false;
        String c = code.trim();
        String low = c.toLowerCase();
        if (low.startsWith("us") || low.startsWith("hk")) return true;
        if (c.matches("[A-Za-z]{1,5}")) return true;
        return c.matches("\\d{5}");
    }

    /** 同花顺行情快照（A股/指数），不支持或失败返回 null */
    private StockQuoteDTO fetchQuoteHithink(String stockCode) {
        if (!hithink.isAvailable() || isUsHkCode(stockCode)) return null;
        String hint = INDEX_MAP.containsKey(stockCode) ? "a-share-index" : null;
        HithinkFinanceService.TickerInfo t = hithink.resolveTicker(stockCode, hint);
        if (t == null || StrUtil.isBlank(t.thscode)) return null;
        if (!t.isAShare() && !t.isIndex()) return null;
        List<JSONObject> items = hithink.snapshot(Collections.singletonList(t.thscode), t.isIndex());
        if (items.isEmpty()) return null;
        JSONObject o = items.get(0);
        StockQuoteDTO dto = new StockQuoteDTO();
        dto.setStockCode(stockCode);
        dto.setStockName(StrUtil.blankToDefault(t.name, stockCode));
        dto.setCurrentPrice(HithinkFinanceService.numBD(o.get("last_price")));
        dto.setChangeAmount(HithinkFinanceService.numBD(o.get("price_change")));
        dto.setChangeRate(HithinkFinanceService.numBD(o.get("price_change_ratio_pct")));
        dto.setOpenPrice(HithinkFinanceService.numBD(o.get("open_price")));
        dto.setHighPrice(HithinkFinanceService.numBD(o.get("high_price")));
        dto.setLowPrice(HithinkFinanceService.numBD(o.get("low_price")));
        dto.setPreClose(HithinkFinanceService.numBD(o.get("prev_price")));
        dto.setVolume(HithinkFinanceService.numLong(o.get("volume")));
        dto.setAmount(HithinkFinanceService.numBD(o.get("turnover")));
        // 估值补充（仅个股，PE_TTM；未披露为 null 不补零）
        if (t.isAShare()) {
            try {
                List<JSONObject> vals = hithink.valuationSnapshot(Collections.singletonList(t.thscode));
                if (!vals.isEmpty()) {
                    dto.setPeRatio(HithinkFinanceService.numBD(vals.get(0).get("pe_ttm")));
                }
            } catch (Exception e) {
                log.warn("估值补充失败 {}: {}", t.thscode, e.getMessage());
            }
        }
        return dto;
    }

    /** 同花顺主要指数批量快照（一次请求） */
    private List<Map<String, Object>> fetchMainIndicesHithink() {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!hithink.isAvailable()) return results;
        try {
            Map<String, HithinkFinanceService.TickerInfo> byName = new LinkedHashMap<>();
            List<String> thscodes = new ArrayList<>();
            for (String name : INDEX_MAP.keySet()) {
                HithinkFinanceService.TickerInfo t = hithink.resolveTicker(name, "a-share-index");
                if (t != null && t.isIndex() && StrUtil.isNotBlank(t.thscode) && !thscodes.contains(t.thscode)) {
                    thscodes.add(t.thscode);
                    byName.put(name, t);
                }
            }
            if (thscodes.isEmpty()) return results;
            List<JSONObject> items = hithink.snapshot(thscodes, true);
            Map<String, JSONObject> byCode = new LinkedHashMap<>();
            for (JSONObject o : items) byCode.put(o.getStr("thscode"), o);
            for (Map.Entry<String, HithinkFinanceService.TickerInfo> e : byName.entrySet()) {
                JSONObject o = byCode.get(e.getValue().thscode);
                if (o == null) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", e.getKey());
                item.put("code", e.getValue().thscode);
                item.put("price", HithinkFinanceService.numBD(o.get("last_price")));
                item.put("change", HithinkFinanceService.numBD(o.get("price_change")));
                item.put("changeRate", HithinkFinanceService.numBD(o.get("price_change_ratio_pct")));
                results.add(item);
            }
        } catch (Exception e) {
            log.warn("同花顺指数快照失败: {}", e.getMessage());
        }
        return results;
    }

    /** 同花顺历史K线（日线，默认前复权），失败/不支持返回空列表 */
    private List<Map<String, Object>> fetchKlineHithink(String stockCode, String startDate, String endDate, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (!hithink.isAvailable() || isUsHkCode(stockCode)) return results;
        try {
            HithinkFinanceService.TickerInfo t = hithink.resolveTicker(stockCode, null);
            if (t == null || (!t.isAShare() && !t.isIndex())) return results;

            long endMs = StrUtil.isNotBlank(endDate) ? HithinkFinanceService.toEndOfDayMs(endDate) : HithinkFinanceService.nowMs();
            long startMs = StrUtil.isNotBlank(startDate) ? HithinkFinanceService.toStartOfDayMs(startDate)
                    : endMs - Math.max(limit, 365) * 86_400_000L;
            long maxSpan = 10L * 366 * 86_400_000L;
            if (endMs - startMs > maxSpan) startMs = endMs - maxSpan;

            List<JSONObject> items = t.isIndex()
                    ? hithink.historicalIndexKline(t.thscode, startMs, endMs, "forward")
                    : hithink.historicalKline(t.thscode, startMs, endMs, "forward");
            for (JSONObject o : items) {
                Map<String, Object> kline = new LinkedHashMap<>();
                kline.put("date", HithinkFinanceService.msToDate(o.getLong("date_ms")));
                kline.put("open", HithinkFinanceService.numStr(o.get("open_price")));
                kline.put("close", HithinkFinanceService.numStr(o.get("close_price")));
                kline.put("high", HithinkFinanceService.numStr(o.get("high_price")));
                kline.put("low", HithinkFinanceService.numStr(o.get("low_price")));
                kline.put("volume", HithinkFinanceService.numStr(o.get("volume")));
                results.add(kline);
            }
        } catch (Exception e) {
            log.warn("同花顺K线获取失败: {}", e.getMessage());
        }
        return results;
    }

    /** 新浪K线兜底 */
    private List<Map<String, Object>> fetchKlineSina(String sinaCode, int limit) {
        List<Map<String, Object>> allData = new ArrayList<>();
        int dataLen = Math.max(limit, 365);
        String url = SINA_KLINE_URL + "?symbol=" + sinaCode + "&scale=240&ma=no&datalen=" + dataLen;
        log.info("请求K线(兜底): {}", url);
        String response = HttpUtil.get(url, httpTimeoutKline);
        if (StrUtil.isBlank(response)) { log.warn("K线响应为空"); return allData; }

        JSONArray arr = JSONUtil.parseArray(response);
        if (arr == null || arr.isEmpty()) { log.warn("K线JSON解析为空"); return allData; }

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
        return allData;
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
            log.warn("缓存解析失败", e);
            return new ArrayList<>();
        }
    }

    private StockQuoteDTO fetchQuote(String stockCode) {
        String tencentCode = toTencentCode(stockCode);
        String response = HttpUtil.get(TENCENT_API_URL + tencentCode, httpTimeoutQuote);
        if (StrUtil.isBlank(response)) return null;
        List<StockQuoteDTO> results = parseTencentResponse(response, List.of(stockCode));
        return results.isEmpty() ? null : results.get(0);
    }

    public String toTencentCode(String stockCode) {
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
            } catch (Exception e) { log.warn("解析行情失败", e); }
        }
        return results;
    }

    /** 解析BigDecimal，失败返回null（避免将解析错误误认为零值） */
    private BigDecimal parseBD(String s) { try { return new BigDecimal(s.trim()); } catch (Exception e) { return null; } }
    private Long parseLong(String s) { try { return Long.parseLong(s.trim().split("\\.")[0]); } catch (Exception e) { return 0L; } }
    private StockQuoteDTO createEmptyQuote(String code) { StockQuoteDTO dto = new StockQuoteDTO(); dto.setStockCode(code); dto.setStockName("未知"); dto.setCurrentPrice(BigDecimal.ZERO); dto.setChangeRate(BigDecimal.ZERO); return dto; }
    private StockSearchDTO convertToSearchDTO(StockInfo e) { StockSearchDTO dto = new StockSearchDTO(); dto.setStockCode(e.getStockCode()); dto.setStockName(e.getStockName()); dto.setMarket(e.getMarket()); dto.setIndustry(e.getIndustry()); return dto; }
}