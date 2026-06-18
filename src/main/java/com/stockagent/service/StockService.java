package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.dto.StockQuoteDTO;
import com.stockagent.dto.StockSearchDTO;
import com.stockagent.entity.StockInfo;
import com.stockagent.entity.StockQuoteCache;
import com.stockagent.mapper.StockInfoMapper;
import com.stockagent.mapper.StockQuoteCacheMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockInfoMapper stockInfoMapper;
    private final StockQuoteCacheMapper stockQuoteCacheMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String TENCENT_API_URL = "http://qt.gtimg.cn/q=";
    private static final String TENCENT_KLINE_URL = "http://web.ifzq.gtimg.cn/appstock/app/fqkline/get";

    // 常用指数代码
    private static final Map<String, String> INDEX_MAP = new LinkedHashMap<>();
    static {
        INDEX_MAP.put("上证指数", "sh000001");
        INDEX_MAP.put("深证成指", "sz399001");
        INDEX_MAP.put("创业板指", "sz399006");
        INDEX_MAP.put("科创50", "sh000688");
        INDEX_MAP.put("上证50", "sh000016");
        INDEX_MAP.put("沪深300", "sh000300");
        INDEX_MAP.put("中证500", "sh000905");
        INDEX_MAP.put("中证1000", "sh000852");
        INDEX_MAP.put("纳斯达克", "us.IXIC");
        INDEX_MAP.put("道琼斯", "us.DJI");
        INDEX_MAP.put("标普500", "us.INX");
        INDEX_MAP.put("恒生指数", "hkHSI");
    }

    // ========== A股搜索 ==========
    public List<StockSearchDTO> searchStock(String keyword) {
        if (StrUtil.isBlank(keyword)) return new ArrayList<>();
        LambdaQueryWrapper<StockInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(StockInfo::getStockCode, keyword).or().like(StockInfo::getStockName, keyword))
               .eq(StockInfo::getStatus, 1).last("LIMIT 20");
        return stockInfoMapper.selectList(wrapper).stream().map(this::convertToSearchDTO).collect(Collectors.toList());
    }

    // ========== 实时行情（A股/美股/港股/指数） ==========
    public StockQuoteDTO getQuote(String stockCode) {
        // 先检查是否是指数名称
        String indexCode = INDEX_MAP.get(stockCode);
        if (indexCode != null) stockCode = indexCode;

        String marketType = detectMarket(stockCode);
        try {
            StockQuoteDTO dto = fetchQuote(stockCode, marketType);
            if (dto != null) return dto;
        } catch (Exception e) {
            log.error("获取行情失败: stockCode={}, error={}", stockCode, e.getMessage());
        }
        return createEmptyQuote(stockCode);
    }

    // ========== 主要指数行情 ==========
    public List<Map<String, Object>> getMainIndices() {
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
        return results;
    }

    // ========== 历史K线数据 ==========
    public List<Map<String, Object>> getKlineData(String stockCode, String startDate, String endDate, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            String tencentCode = toTencentCode(stockCode);
            String url = TENCENT_KLINE_URL + "?param=" + tencentCode + ",day," + startDate + "," + endDate + "," + limit + ",qfq";
            String response = HttpUtil.get(url, 10000);
            if (StrUtil.isBlank(response)) return results;

            JSONObject json = JSONUtil.parseObj(response);
            JSONObject data = json.getJSONObject("data");
            if (data == null) return results;

            JSONObject stockData = data.getJSONObject(tencentCode);
            if (stockData == null) return results;

            JSONArray dayArr = stockData.getJSONArray("qfqday");
            if (dayArr == null) dayArr = stockData.getJSONArray("day");
            if (dayArr == null) return results;

            for (int i = 0; i < dayArr.size(); i++) {
                JSONArray day = dayArr.getJSONArray(i);
                if (day.size() >= 5) {
                    Map<String, Object> kline = new LinkedHashMap<>();
                    kline.put("date", day.getStr(0));
                    kline.put("open", day.getStr(1));
                    kline.put("close", day.getStr(2));
                    kline.put("high", day.getStr(3));
                    kline.put("low", day.getStr(4));
                    if (day.size() > 5) kline.put("volume", day.getStr(5));
                    results.add(kline);
                }
            }
        } catch (Exception e) {
            log.error("获取K线数据失败: {}", e.getMessage());
        }
        return results;
    }

    // ========== 全球股票搜索 ==========
    public List<Map<String, String>> searchGlobalStock(String keyword) {
        List<Map<String, String>> results = new ArrayList<>();
        // 先检查是否匹配指数名称
        for (Map.Entry<String, String> entry : INDEX_MAP.entrySet()) {
            if (entry.getKey().contains(keyword) || keyword.contains(entry.getKey())) {
                Map<String, String> item = new HashMap<>();
                item.put("code", entry.getValue());
                item.put("name", entry.getKey());
                item.put("market", "指数");
                results.add(item);
            }
        }
        // 搜A股
        List<StockSearchDTO> aStocks = searchStock(keyword);
        for (StockSearchDTO s : aStocks) {
            Map<String, String> item = new HashMap<>();
            item.put("code", s.getStockCode());
            item.put("name", s.getStockName());
            item.put("market", "A股");
            results.add(item);
        }
        // 英文字母搜美股
        if (keyword.matches("[a-zA-Z]+")) {
            String usCode = "us" + keyword.toUpperCase();
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + usCode, 3000);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + usCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]);
                        item.put("name", parts[1]);
                        item.put("market", "美股");
                        item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception ignored) {}
        }
        // 纯数字搜港股
        if (keyword.matches("\\d+")) {
            String hkCode = "hk" + String.format("%05d", Integer.parseInt(keyword));
            try {
                String resp = HttpUtil.get(TENCENT_API_URL + hkCode, 3000);
                if (StrUtil.isNotBlank(resp) && !resp.contains("v_" + hkCode + "=\"\"")) {
                    String[] parts = resp.split("~");
                    if (parts.length > 3) {
                        Map<String, String> item = new HashMap<>();
                        item.put("code", parts[2]);
                        item.put("name", parts[1]);
                        item.put("market", "港股");
                        item.put("price", parts[3]);
                        results.add(item);
                    }
                }
            } catch (Exception ignored) {}
        }
        return results;
    }

    // ========== 私有方法 ==========
    private String detectMarket(String code) {
        if (code == null) return "A";
        String c = code.toUpperCase().trim();
        if (c.startsWith("US.") || c.startsWith("HKHS")) return "INDEX";
        if (c.matches("[A-Z]{1,5}")) return "US";
        if (c.matches("\\d{5}")) return "HK";
        return "A";
    }

    private StockQuoteDTO fetchQuote(String stockCode, String marketType) {
        String tencentCode = toTencentCode(stockCode);
        String response = HttpUtil.get(TENCENT_API_URL + tencentCode, 5000);
        if (StrUtil.isBlank(response)) return null;
        List<StockQuoteDTO> results = parseTencentResponse(response, List.of(stockCode));
        return results.isEmpty() ? null : results.get(0);
    }

    private String toTencentCode(String stockCode) {
        if (stockCode == null) return "";
        String code = stockCode.trim();
        // 已有前缀
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("hk") || code.startsWith("us")) return code;
        if (code.startsWith("SH") || code.startsWith("SZ") || code.startsWith("HK") || code.startsWith("US")) return code.toLowerCase();
        // 美股指数
        if (code.startsWith("us.")) return code;
        // 纯字母=美股
        if (code.matches("[A-Z]{1,5}")) return "us" + code;
        // 5位数字=港股
        if (code.matches("\\d{5}")) return "hk" + code;
        // A股
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
                dto.setStockCode(p[2]);
                dto.setStockName(p[1]);
                dto.setCurrentPrice(parseBD(p[3]));
                dto.setPreClose(parseBD(p[4]));
                dto.setOpenPrice(parseBD(p[5]));
                if (p.length > 6) dto.setVolume(parseLong(p[6]));
                if (p.length > 31) dto.setChangeAmount(parseBD(p[31]));
                if (p.length > 32) dto.setChangeRate(parseBD(p[32]));
                if (p.length > 33) dto.setHighPrice(parseBD(p[33]));
                if (p.length > 34) dto.setLowPrice(parseBD(p[34]));
                if (p.length > 37) dto.setAmount(parseBD(p[37]));
                results.add(dto);
            } catch (Exception e) { log.warn("解析行情数据失败: {}", e.getMessage()); }
        }
        return results;
    }

    private BigDecimal parseBD(String s) { try { return new BigDecimal(s.trim()); } catch (Exception e) { return BigDecimal.ZERO; } }
    private Long parseLong(String s) { try { return Long.parseLong(s.trim().split("\\.")[0]); } catch (Exception e) { return 0L; } }
    private StockQuoteDTO createEmptyQuote(String code) { StockQuoteDTO dto = new StockQuoteDTO(); dto.setStockCode(code); dto.setStockName("未知"); dto.setCurrentPrice(BigDecimal.ZERO); dto.setChangeRate(BigDecimal.ZERO); return dto; }
    private StockSearchDTO convertToSearchDTO(StockInfo e) { StockSearchDTO dto = new StockSearchDTO(); dto.setStockCode(e.getStockCode()); dto.setStockName(e.getStockName()); dto.setMarket(e.getMarket()); dto.setIndustry(e.getIndustry()); return dto; }
    private StockQuoteDTO convertToQuoteDTO(StockQuoteCache e) { StockQuoteDTO dto = new StockQuoteDTO(); dto.setStockCode(e.getStockCode()); dto.setStockName(e.getStockName()); dto.setCurrentPrice(e.getCurrentPrice()); dto.setOpenPrice(e.getOpenPrice()); dto.setHighPrice(e.getHighPrice()); dto.setLowPrice(e.getLowPrice()); dto.setPreClose(e.getPreClose()); dto.setChangeAmount(e.getChangeAmount()); dto.setChangeRate(e.getChangeRate()); dto.setVolume(e.getVolume()); dto.setAmount(e.getAmount()); return dto; }
}
