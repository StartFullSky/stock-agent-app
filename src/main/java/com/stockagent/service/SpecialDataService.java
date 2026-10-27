package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 同花顺特色数据服务：涨停/跌停/炸板/连板天梯、热股榜/飙升榜、异动原因、龙虎榜，
 * 以及估值快照与财务指标的业务封装。全部经 HithinkFinanceService 校验信封 code==0；
 * 不可用或失败返回空结果，不使用模拟数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecialDataService {

    private final HithinkFinanceService hithink;

    private static final String PATH_LIMIT_UP = "/api/a-share/special-data/limit-up-pool";
    private static final String PATH_LIMIT_DOWN = "/api/a-share/special-data/limit-down-pool";
    private static final String PATH_LIMIT_BREAK = "/api/a-share/special-data/limit-break-pool";
    private static final String PATH_LIMIT_LADDER = "/api/a-share/special-data/limit-up-ladder";
    private static final String PATH_HOT = "/api/a-share/special-data/hot-stock-list";
    private static final String PATH_SKYROCKET = "/api/a-share/special-data/skyrocket-list";
    private static final String PATH_ANOMALY_STOCK = "/api/a-share/special-data/anomaly-analysis-stock";
    private static final String PATH_DRAGON_TIGER = "/api/a-share/special-data/dragon-tiger-list";

    /** ---------- 涨跌停 ---------- */

    /** 涨停股票池（含连板/封单/涨停原因），date 为 yyyy-MM-dd，空=当日 */
    public Map<String, Object> limitUpPool(String date, int size) {
        return pool(PATH_LIMIT_UP, date, size);
    }

    public Map<String, Object> limitDownPool(String date, int size) {
        return pool(PATH_LIMIT_DOWN, date, size);
    }

    public Map<String, Object> limitBreakPool(String date, int size) {
        return pool(PATH_LIMIT_BREAK, date, size);
    }

    private Map<String, Object> pool(String path, String date, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", new ArrayList<>());
        if (!hithink.isAvailable()) return result;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(date)) params.put("date_ms", String.valueOf(HithinkFinanceService.toStartOfDayMs(date)));
            params.put("page", "1");
            params.put("size", String.valueOf(Math.min(Math.max(size, 1), 200)));
            JSONObject data = hithink.specialData(path, params);
            if (data == null) return result;
            result.put("date", date);
            JSONObject pg = data.getJSONObject("pagination");
            result.put("total", pg != null ? pg.getInt("total", 0) : data.getInt("total", 0));
            result.put("timestamp", data.getLong("timestamp"));
            List<Map<String, Object>> items = new ArrayList<>();
            JSONArray arr = data.getJSONArray("item");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("thscode", o.getStr("thscode"));
                    m.put("name", o.getStr("name"));
                    m.put("lastPrice", o.getBigDecimal("last_price"));
                    m.put("changeRatioPct", o.getBigDecimal("price_change_ratio_pct"));
                    m.put("limitUpTime", o.getStr("limit_up_time"));
                    m.put("limitUpReason", o.getStr("limit_up_reason"));
                    m.put("continueDayText", o.getStr("continue_day_text"));
                    m.put("continueDayCnt", o.getInt("continue_day_cnt"));
                    m.put("sealMoney", o.getBigDecimal("seal_money"));
                    m.put("isSt", o.getBool("is_st"));
                    items.add(m);
                }
            }
            result.put("items", items);
        } catch (Exception e) {
            log.warn("特色数据获取失败 {}: {}", path, e.getMessage());
        }
        return result;
    }

    /** 连板天梯（近30个交易日连板梯队矩阵），无入参 */
    public Map<String, Object> limitUpLadder() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!hithink.isAvailable()) return result;
        JSONObject data = hithink.specialData(PATH_LIMIT_LADDER, Collections.emptyMap());
        if (data != null) {
            result.put("timestamp", data.getLong("timestamp"));
            result.put("data", data);
        }
        return result;
    }

    /** ---------- 热榜 ---------- */

    /** A股热股榜 Top30 / 飙升榜 Top30，period=day|hour */
    public List<Map<String, Object>> hotStockList(String period, boolean skyrocket) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!hithink.isAvailable()) return items;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("period", StrUtil.blankToDefault(period, "day"));
            JSONObject data = hithink.specialData(skyrocket ? PATH_SKYROCKET : PATH_HOT, params);
            if (data == null) return items;
            JSONArray arr = data.getJSONArray("item");
            if (arr == null) return items;
            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("thscode", o.getStr("thscode"));
                m.put("name", o.getStr("name"));
                m.put("rank", o.getInt("rank"));
                m.put("heat", o.getStr("heat"));
                items.add(m);
            }
        } catch (Exception e) {
            log.warn("热股榜获取失败: {}", e.getMessage());
        }
        return items;
    }

    /** ---------- 异动原因 ---------- */

    /** 个股异动原因（按 thscode 查） */
    public List<Map<String, Object>> anomalyByStock(String thscode) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (!hithink.isAvailable() || StrUtil.isBlank(thscode)) return items;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("thscodes", thscode);
            JSONObject data = hithink.specialData(PATH_ANOMALY_STOCK, params);
            if (data == null) return items;
            JSONArray arr = data.getJSONArray("item");
            if (arr == null) return items;
            for (int i = 0; i < arr.size(); i++) {
                items.add(new LinkedHashMap<>(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            log.warn("异动原因获取失败 {}: {}", thscode, e.getMessage());
        }
        return items;
    }

    /** ---------- 龙虎榜 ---------- */

    /** 龙虎榜，date=yyyy-MM-dd（空=最近交易日），boardType=all|org|hot_money */
    public Map<String, Object> dragonTiger(String date, String boardType) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!hithink.isAvailable()) return result;
        try {
            Map<String, String> params = new LinkedHashMap<>();
            if (StrUtil.isNotBlank(date)) params.put("date", date);
            params.put("board_type", StrUtil.blankToDefault(boardType, "all"));
            JSONObject data = hithink.specialData(PATH_DRAGON_TIGER, params);
            if (data == null) return result;
            result.put("tradeDate", data.getStr("trade_date"));
            result.put("boardType", data.getStr("board_type"));
            result.put("stockCount", data.getInt("stock_count"));
            result.put("stocks", toListOfMaps(data.getJSONArray("stock_items")));
            result.put("hotMoney", toListOfMaps(data.getJSONArray("hot_money_items")));
        } catch (Exception e) {
            log.warn("龙虎榜获取失败: {}", e.getMessage());
        }
        return result;
    }

    /** ---------- 估值 / 财务 ---------- */

    /** 估值快照（PE_TTM/PB/PS/PCF），单标的；不补零，null 表示未披露 */
    public Map<String, Object> valuation(String thscode) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!hithink.isAvailable() || StrUtil.isBlank(thscode)) return result;
        try {
            List<JSONObject> items = hithink.valuationSnapshot(Collections.singletonList(thscode));
            if (items.isEmpty()) return result;
            JSONObject o = items.get(0);
            result.put("thscode", o.getStr("thscode"));
            result.put("name", o.getStr("name"));
            result.put("peTtm", o.getBigDecimal("pe_ttm"));
            result.put("peMrq", o.getBigDecimal("pe_mrq"));
            result.put("pbMrq", o.getBigDecimal("pb_mrq"));
            result.put("psTtm", o.getBigDecimal("ps_ttm"));
            result.put("pcfTtm", o.getBigDecimal("pcf_ttm"));
        } catch (Exception e) {
            log.warn("估值获取失败 {}: {}", thscode, e.getMessage());
        }
        return result;
    }

    /** 财务指标（五类能力），report 形如 2025-1/2025-2/2025-3/2025-4 */
    public Map<String, Object> financialIndicators(String thscode, String report) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!hithink.isAvailable() || StrUtil.isBlank(thscode) || StrUtil.isBlank(report)) return result;
        JSONObject data = hithink.financialIndicators(thscode, report);
        if (data == null) return result;
        result.put("thscode", data.getStr("thscode"));
        result.put("report", data.getStr("report"));
        List<Map<String, Object>> abilities = new ArrayList<>();
        JSONArray arr = data.getJSONArray("abilities");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject a = arr.getJSONObject(i);
                Map<String, Object> ability = new LinkedHashMap<>();
                ability.put("ability", a.getStr("ability"));
                List<Map<String, Object>> indicators = new ArrayList<>();
                JSONArray inds = a.getJSONArray("indicators");
                if (inds != null) {
                    for (int j = 0; j < inds.size(); j++) {
                        JSONObject ind = inds.getJSONObject(j);
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("indexId", ind.getStr("index_id"));
                        m.put("value", ind.getStr("value"));
                        indicators.add(m);
                    }
                }
                ability.put("indicators", indicators);
                abilities.add(ability);
            }
        }
        result.put("abilities", abilities);
        return result;
    }

    /** 财务指标的紧凑文本（供 AI 工具用） */
    public String financialIndicatorsText(String thscode, String report) {
        Map<String, Object> r = financialIndicators(thscode, report);
        if (r.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("报告期: ").append(r.get("report")).append('\n');
        for (Map<String, Object> ability : castList(r.get("abilities"))) {
            sb.append('[').append(ability.get("ability")).append("]\n");
            for (Map<String, Object> ind : castList(ability.get("indicators"))) {
                Object v = ind.get("value");
                sb.append("  ").append(ind.get("indexId")).append(" = ").append(v == null ? "未披露" : v).append('\n');
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object o) {
        return o instanceof List ? (List<Map<String, Object>>) o : Collections.emptyList();
    }

    private List<Map<String, Object>> toListOfMaps(JSONArray arr) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (arr == null) return items;
        for (int i = 0; i < arr.size(); i++) {
            items.add(new LinkedHashMap<>(arr.getJSONObject(i)));
        }
        return items;
    }
}
