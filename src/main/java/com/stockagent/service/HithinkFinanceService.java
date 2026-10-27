package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 同花顺金融数据服务（fuyao.aicubes.cn）客户端。
 * 作为股票行情/财务/搜索的主数据源；统一信封 {code,message,request_id,data}，
 * 成功判定 code==0。限流(4001)/5xxx 有界退避重试，1xxx/2xxx 不重试。
 * 凭据：环境变量 HITHINK_FINANCE_API_KEY 或用户级 credentials.env。
 */
@Slf4j
@Service
public class HithinkFinanceService {

    private final StringRedisTemplate redisTemplate;

    @Value("${hithink.enabled:true}")
    private boolean enabled = true;

    @Value("${hithink.base-url:https://fuyao.aicubes.cn}")
    private String baseUrl = "https://fuyao.aicubes.cn";

    @Value("${hithink.api-key:}")
    private String apiKey;

    @Value("${hithink.timeout:8000}")
    private int timeout = 8000;

    private static final String CACHE_PREFIX_TICKER = "stock:thscode:";
    private static final long TICKER_CACHE_TTL_DAYS = 7;
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");

    public HithinkFinanceService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** ---------- 凭据 ---------- */

    private String resolveApiKey() {
        if (StrUtil.isNotBlank(apiKey)) return apiKey.trim();
        String env = System.getenv("HITHINK_FINANCE_API_KEY");
        if (StrUtil.isNotBlank(env)) return env.trim();
        String fromFile = readCredentialsFile();
        if (StrUtil.isNotBlank(fromFile)) return fromFile;
        return null;
    }

    private String readCredentialsFile() {
        try {
            List<Path> candidates = new ArrayList<>();
            String appData = System.getenv("APPDATA");
            if (StrUtil.isNotBlank(appData)) {
                candidates.add(Paths.get(appData, "hithink-finance", "credentials.env"));
            }
            String xdg = System.getenv("XDG_CONFIG_HOME");
            String home = System.getProperty("user.home");
            Path linuxDir = StrUtil.isNotBlank(xdg) ? Paths.get(xdg) : Paths.get(home, ".config");
            candidates.add(linuxDir.resolve("hithink-finance").resolve("credentials.env"));
            for (Path p : candidates) {
                if (!Files.isRegularFile(p)) continue;
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("HITHINK_FINANCE_API_KEY=")) {
                        String v = t.substring("HITHINK_FINANCE_API_KEY=".length()).trim();
                        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                            v = v.substring(1, v.length() - 1);
                        }
                        if (StrUtil.isNotBlank(v)) return v;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("读取 hithink credentials.env 失败: {}", e.getMessage());
        }
        return null;
    }

    public boolean isAvailable() {
        return enabled && StrUtil.isNotBlank(resolveApiKey());
    }

    /** ---------- 低层请求 ---------- */

    /**
     * GET {base-url}{path}?params，校验业务信封 code==0。
     * 返回 data 对象；业务失败/网络失败返回 null（调用方负责回退）。
     */
    private JSONObject request(String path, Map<String, String> params) {
        String key = resolveApiKey();
        if (!enabled || StrUtil.isBlank(key)) return null;

        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (StrUtil.isBlank(e.getValue())) continue;
            if (qs.length() > 0) qs.append('&');
            qs.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        String url = baseUrl + path + (qs.length() > 0 ? "?" + qs : "");

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String body = HttpRequest.get(url)
                        .header("X-api-key", key)
                        .timeout(timeout)
                        .execute().body();
                if (StrUtil.isBlank(body)) {
                    log.warn("同花顺接口空响应: {} (attempt {})", path, attempt);
                } else {
                    JSONObject envelope = JSONUtil.parseObj(body);
                    int code = envelope.getInt("code", -1);
                    if (code == 0) {
                        JSONObject data = envelope.getJSONObject("data");
                        return data != null ? data : new JSONObject();
                    }
                    String requestId = envelope.getStr("request_id", "-");
                    String message = envelope.getStr("message", "");
                    if (code == 4001 || code >= 5000) {
                        log.warn("同花顺接口可重试错误 code={} request_id={} msg={} (attempt {})",
                                code, requestId, message, attempt);
                    } else {
                        // 1xxx/2xxx/3xxx：参数或数据状态问题，不重试
                        log.warn("同花顺接口业务错误 code={} request_id={} msg={}", code, requestId, message);
                        return null;
                    }
                }
            } catch (Exception e) {
                log.warn("同花顺接口请求异常 {} (attempt {}): {}", path, attempt, e.getMessage());
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }

    /** ---------- 标的检索 / thscode 消歧 ---------- */

    public static class TickerInfo {
        public String thscode;
        public String name;
        public String assetType;

        public boolean isIndex() {
            return "a-share-index".equals(assetType);
        }

        public boolean isAShare() {
            return "a-share".equals(assetType);
        }
    }

    /**
     * 把自然语言代码/名称消歧为唯一 thscode（带市场后缀，如 600519.SH）。
     * 优先 /api/meta/tickers/search；已有 thscode 前缀（sh/sz/bj）时直接规范化。
     * Redis 缓存 7 天。找不到返回 null。
     */
    public TickerInfo resolveTicker(String codeOrName, String assetTypeHint) {
        if (StrUtil.isBlank(codeOrName)) return null;
        String q = codeOrName.trim();

        // 已是完整 thscode 或 sh600519 风格：仅规范化查询串，仍走 meta 检索验证资产类型
        String query = q;
        boolean explicitCode = false;
        if (q.matches("\\d{6}\\.(SH|SZ|BJ|TI)")) {
            query = q.toUpperCase();
            explicitCode = true;
        } else if (q.matches("(?i)(sh|sz|bj)\\d{6}")) {
            query = q.substring(2) + "." + q.substring(0, 2).toUpperCase();
            explicitCode = true;
        }

        String cacheKey = CACHE_PREFIX_TICKER + q + ":" + StrUtil.nullToEmpty(assetTypeHint);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) return JSONUtil.toBean(cached, TickerInfo.class);
        } catch (Exception e) {
            log.warn("thscode 缓存读取失败: {}", e.getMessage());
        }

        TickerInfo resolved = searchTickerRemote(query, assetTypeHint);
        if (resolved == null && explicitCode) {
            resolved = guessFromThscode(query);
        }
        if (resolved != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(resolved),
                        TICKER_CACHE_TTL_DAYS, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("thscode 缓存写入失败: {}", e.getMessage());
            }
        }
        return resolved;
    }

    /** meta 检索不可用时的兜底：按 thscode 结构推断资产类型（仅在显式代码时使用） */
    private TickerInfo guessFromThscode(String thscode) {
        TickerInfo t = new TickerInfo();
        t.thscode = thscode;
        t.name = thscode;
        String ticker = thscode.split("\\.")[0];
        String ex = thscode.split("\\.")[1];
        boolean index = thscode.endsWith(".TI")
                || ("SH".equals(ex) && !ticker.startsWith("6"))
                || ("SZ".equals(ex) && ticker.startsWith("399"));
        t.assetType = index ? "a-share-index" : "a-share";
        return t;
    }

    private TickerInfo searchTickerRemote(String q, String assetTypeHint) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", q);
        params.put("limit", "10");
        if (StrUtil.isNotBlank(assetTypeHint)) params.put("asset_type", assetTypeHint);
        JSONObject data = request("/api/meta/tickers/search", params);
        if (data == null) return null;
        JSONArray items = data.getJSONArray("item");
        if (items == null || items.isEmpty()) return null;

        List<TickerInfo> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JSONObject o = items.getJSONObject(i);
            TickerInfo t = new TickerInfo();
            t.thscode = o.getStr("thscode");
            t.name = o.getStr("name");
            t.assetType = o.getStr("asset_type");
            if (t.thscode != null) all.add(t);
        }
        // 精确匹配代码或名称优先（如“上证指数”按名称精确命中）
        List<TickerInfo> exact = new ArrayList<>();
        for (TickerInfo t : all) {
            if (q.equalsIgnoreCase(t.thscode) || q.equals(t.thscode.split("\\.")[0]) || q.equals(t.name)) {
                exact.add(t);
            }
        }
        List<TickerInfo> pool = exact.isEmpty() ? all : exact;
        if (StrUtil.isNotBlank(assetTypeHint)) {
            for (TickerInfo t : pool) {
                if (assetTypeHint.equals(t.assetType)) return t;
            }
        } else {
            // 无提示时优先股票（如 000001 → 平安银行 000001.SZ，而非上证指数 000001.SH）
            for (TickerInfo t : pool) {
                if ("a-share".equals(t.assetType)) return t;
            }
        }
        return pool.get(0);
    }

    /** 公开的标的搜索（供搜索接口用） */
    public List<Map<String, String>> searchTickers(String q, String assetType, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", q);
        params.put("limit", String.valueOf(Math.min(Math.max(limit, 1), 50)));
        if (StrUtil.isNotBlank(assetType)) params.put("asset_type", assetType);
        JSONObject data = request("/api/meta/tickers/search", params);
        List<Map<String, String>> results = new ArrayList<>();
        if (data == null) return results;
        JSONArray items = data.getJSONArray("item");
        if (items == null) return results;
        for (int i = 0; i < items.size(); i++) {
            JSONObject o = items.getJSONObject(i);
            Map<String, String> r = new LinkedHashMap<>();
            r.put("thscode", o.getStr("thscode"));
            r.put("code", o.getStr("ticker"));
            r.put("name", o.getStr("name"));
            r.put("assetType", o.getStr("asset_type"));
            r.put("exchange", o.getStr("exchange"));
            results.add(r);
        }
        return results;
    }

    /** ---------- 行情快照 ---------- */

    /**
     * 批量行情快照。index=true 走指数接口，否则走 A 股接口。
     * 返回原始 item 数组（保留 null，不补零）。
     */
    public List<JSONObject> snapshot(List<String> thscodes, boolean index) {
        List<JSONObject> results = new ArrayList<>();
        if (thscodes == null || thscodes.isEmpty()) return results;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("thscodes", String.join(",", thscodes));
        String path = index ? "/api/a-share-index/prices/snapshot" : "/api/a-share/prices/snapshot";
        JSONObject data = request(path, params);
        if (data == null) return results;
        JSONArray items = data.getJSONArray("item");
        if (items == null) return results;
        for (int i = 0; i < items.size(); i++) {
            results.add(items.getJSONObject(i));
        }
        return results;
    }

    /** ---------- 历史 K 线 ---------- */

    /**
     * 历史日 K。thscode 单标的；[start,end] 毫秒时间戳；adjust 默认前复权 forward。
     * 返回 item 列表（date_ms/open_price/...），不补零、不造数据。
     */
    public List<JSONObject> historicalKline(String thscode, long startMs, long endMs, String adjust) {
        List<JSONObject> results = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("thscode", thscode);
        params.put("interval", "1d");
        params.put("start", String.valueOf(startMs));
        params.put("end", String.valueOf(endMs));
        params.put("adjust", StrUtil.blankToDefault(adjust, "forward"));
        JSONObject data = request("/api/a-share/prices/historical", params);
        if (data == null) return results;
        JSONArray items = data.getJSONArray("item");
        if (items == null) return results;
        for (int i = 0; i < items.size(); i++) {
            results.add(items.getJSONObject(i));
        }
        return results;
    }

    public List<JSONObject> historicalIndexKline(String thscode, long startMs, long endMs, String adjust) {
        List<JSONObject> results = new ArrayList<>();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("thscode", thscode);
        params.put("interval", "1d");
        params.put("start", String.valueOf(startMs));
        params.put("end", String.valueOf(endMs));
        params.put("adjust", StrUtil.blankToDefault(adjust, "forward"));
        JSONObject data = request("/api/a-share-index/prices/historical", params);
        if (data == null) return results;
        JSONArray items = data.getJSONArray("item");
        if (items == null) return results;
        for (int i = 0; i < items.size(); i++) {
            results.add(items.getJSONObject(i));
        }
        return results;
    }

    /** ---------- 日期工具 ---------- */

    public static long toStartOfDayMs(String yyyyMMdd) {
        LocalDate d = LocalDate.parse(yyyyMMdd);
        return d.atStartOfDay(CN_ZONE).toInstant().toEpochMilli();
    }

    public static long toEndOfDayMs(String yyyyMMdd) {
        LocalDate d = LocalDate.parse(yyyyMMdd);
        return d.atTime(23, 59, 59).atZone(CN_ZONE).toInstant().toEpochMilli();
    }

    public static long nowMs() {
        return Instant.now().toEpochMilli();
    }

    public static String msToDate(long ms) {
        return Instant.ofEpochMilli(ms).atZone(CN_ZONE).toLocalDate().toString();
    }

    /** 数值转字符串，保留原始精度；null 不补零 */
    public static String numStr(Object v) {
        if (v == null) return null;
        if (v instanceof Number) {
            BigDecimal bd = new BigDecimal(v.toString());
            return bd.stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    public static BigDecimal numBD(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public static Long numLong(Object v) {
        if (v == null) return null;
        try {
            return new BigDecimal(v.toString()).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    public static Duration tickerCacheTtl() {
        return Duration.ofDays(TICKER_CACHE_TTL_DAYS);
    }
}
