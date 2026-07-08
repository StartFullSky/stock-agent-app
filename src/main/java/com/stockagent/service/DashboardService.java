package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String TENCENT_API_URL = "http://qt.gtimg.cn/q=";
    private static final String TENCENT_RANK_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/rankTopN/getHsRank?p=1&o=1&l=10&v=list_data";
    private static final String TENCENT_NEWS_URL = "https://proxy.finance.qq.com/ifzqgtimg/appstock/news/info/search?keyword=&page=1&length=20";

    private static final Map<String, String> INDEX_MAP = new LinkedHashMap<>();
    static {
        INDEX_MAP.put("上证指数", "sh000001");
        INDEX_MAP.put("深证成指", "sz399001");
        INDEX_MAP.put("创业板指", "sz399006");
        INDEX_MAP.put("沪深300", "sh000300");
        INDEX_MAP.put("上证50", "sh000016");
        INDEX_MAP.put("中证500", "sh000905");
        INDEX_MAP.put("纳斯达克", "usNDAQ");
        INDEX_MAP.put("道琼斯", "usDJI");
        INDEX_MAP.put("恒生指数", "hkHSI");
    }

    /**
     * Get market overview - indices
     */
    public List<Map<String, Object>> getIndices() {
        List<Map<String, Object>> result = new ArrayList<>();
        String codes = String.join(",", INDEX_MAP.values());
        try {
            String resp = HttpUtil.get(TENCENT_API_URL + codes, 5000);
            String[] lines = resp.split(";");
            Map<String, String> codeToName = new LinkedHashMap<>();
            INDEX_MAP.forEach((name, code) -> codeToName.put(code, name));

            for (String line : lines) {
                line = line.trim();
                if (!line.contains("=")) continue;
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).trim().replace("v_", "");
                String val = line.substring(eq + 1).trim().replace("\"", "");
                String name = codeToName.get(key);
                if (name == null || val.isEmpty()) continue;

                String[] parts = val.split("~");
                if (parts.length >= 4) {
                    Map<String, Object> idx = new LinkedHashMap<>();
                    idx.put("name", name);
                    idx.put("price", parts[1]);
                    idx.put("change", parts[2]);
                    idx.put("changeRate", parts[3]);
                    result.add(idx);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch indices", e);
        }
        return result;
    }

    /**
     * Get top gainers/losers
     */
    public Map<String, Object> getRankings() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Top gainers
            String gainResp = HttpUtil.get(
                "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/rankTopN/getHsRank?p=1&o=1&l=10&v=list_data",
                5000);
            result.put("gainers", parseRankData(gainResp));

            // Top losers
            String loseResp = HttpUtil.get(
                "https://proxy.finance.qq.com/ifzqgtimg/appstock/app/rankTopN/getHsRank?p=1&o=0&l=10&v=list_data",
                5000);
            result.put("losers", parseRankData(loseResp));
        } catch (Exception e) {
            log.error("Failed to fetch rankings", e);
            result.put("gainers", new ArrayList<>());
            result.put("losers", new ArrayList<>());
        }
        return result;
    }

    private List<Map<String, String>> parseRankData(String resp) {
        List<Map<String, String>> list = new ArrayList<>();
        if (StrUtil.isBlank(resp)) return list;
        try {
            // Try to extract JSON from response
            int start = resp.indexOf("{");
            int end = resp.lastIndexOf("}");
            if (start >= 0 && end > start) {
                String json = resp.substring(start, end + 1);
                JSONObject obj = JSONUtil.parseObj(json);
                JSONObject data = obj.getJSONObject("data");
                if (data != null) {
                    JSONArray items = data.getJSONArray("list");
                    if (items != null) {
                        for (int i = 0; i < items.size(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            Map<String, String> m = new LinkedHashMap<>();
                            m.put("code", item.getStr("c", ""));
                            m.put("name", item.getStr("n", ""));
                            m.put("price", item.getStr("p", ""));
                            m.put("changeRate", item.getStr("zdp", ""));
                            list.add(m);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Parse rank data failed", e);
        }
        return list;
    }

    /**
     * Get latest financial news
     */
    public List<Map<String, String>> getNews() {
        List<Map<String, String>> news = new ArrayList<>();
        try {
            String resp = HttpUtil.get(TENCENT_NEWS_URL, 5000);
            JSONObject obj = JSONUtil.parseObj(resp);
            JSONArray articles = obj.getByPath("data.articles", JSONArray.class);
            if (articles != null) {
                for (int i = 0; i < Math.min(articles.size(), 15); i++) {
                    JSONObject a = articles.getJSONObject(i);
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("title", a.getStr("title", ""));
                    item.put("time", a.getStr("time", ""));
                    item.put("source", a.getStr("source", ""));
                    item.put("url", a.getStr("url", ""));
                    news.add(item);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch news", e);
        }
        return news;
    }

    /**
     * Generate a text summary for AI analysis
     */
    public String generateSummaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 今日A股市场概览\n\n");

        // Indices
        List<Map<String, Object>> indices = getIndices();
        sb.append("## 大盘指数\n");
        for (Map<String, Object> idx : indices) {
            sb.append(String.format("- %s: %s (%s%%)\n",
                idx.get("name"), idx.get("price"), idx.get("changeRate")));
        }
        sb.append("\n");

        // Rankings
        Map<String, Object> rankings = getRankings();
        @SuppressWarnings("unchecked")
        List<Map<String, String>> gainers = (List<Map<String, String>>) rankings.get("gainers");
        if (gainers != null && !gainers.isEmpty()) {
            sb.append("## 涨幅榜 Top 5\n");
            gainers.stream().limit(5).forEach(g ->
                sb.append(String.format("- %s(%s): %s%%\n", g.get("name"), g.get("code"), g.get("changeRate")))
            );
            sb.append("\n");
        }

        // News
        List<Map<String, String>> news = getNews();
        if (!news.isEmpty()) {
            sb.append("## 财经要闻\n");
            news.stream().limit(5).forEach(n ->
                sb.append(String.format("- %s (%s)\n", n.get("title"), n.get("source")))
            );
        }

        return sb.toString();
    }
}
