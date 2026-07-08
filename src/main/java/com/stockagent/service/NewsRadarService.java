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
public class NewsRadarService {

    private static final String NEWS_API = "https://proxy.finance.qq.com/ifzqgtimg/appstock/news/info/search";

    // News categories
    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("大盘", "大盘 指数 走势");
        CATEGORY_KEYWORDS.put("政策", "政策 监管 央行");
        CATEGORY_KEYWORDS.put("行业", "行业 板块 赛道");
        CATEGORY_KEYWORDS.put("个股", "涨停 跌停 异动");
        CATEGORY_KEYWORDS.put("国际", "美股 港股 全球");
        CATEGORY_KEYWORDS.put("基金", "基金 ETF 基金经理");
    }

    /**
     * Search news by keyword
     */
    public List<Map<String, String>> searchNews(String keyword, int page, int limit) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            String url = NEWS_API + "?keyword=" + (StrUtil.isBlank(keyword) ? "" : keyword)
                + "&page=" + page + "&length=" + limit;
            String resp = HttpUtil.get(url, 5000);
            JSONObject obj = JSONUtil.parseObj(resp);
            JSONArray articles = obj.getByPath("data.articles", JSONArray.class);
            if (articles != null) {
                for (int i = 0; i < articles.size(); i++) {
                    JSONObject a = articles.getJSONObject(i);
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("title", a.getStr("title", ""));
                    item.put("summary", a.getStr("summary", ""));
                    item.put("time", a.getStr("time", ""));
                    item.put("source", a.getStr("source", ""));
                    item.put("url", a.getStr("url", ""));
                    item.put("category", a.getStr("category", ""));
                    result.add(item);
                }
            }
        } catch (Exception e) {
            log.error("Failed to search news: {}", keyword, e);
        }
        return result;
    }

    /**
     * Get news grouped by category
     */
    public Map<String, List<Map<String, String>>> getNewsByCategory() {
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            List<Map<String, String>> news = searchNews(entry.getValue(), 1, 5);
            if (!news.isEmpty()) {
                result.put(entry.getKey(), news);
            }
        }
        return result;
    }

    /**
     * Get latest news (all categories mixed)
     */
    public List<Map<String, String>> getLatestNews(int limit) {
        return searchNews("", 1, limit);
    }

    /**
     * Get stock-specific news
     */
    public List<Map<String, String>> getStockNews(String stockCode, String stockName) {
        String keyword = StrUtil.isNotBlank(stockName) ? stockName : stockCode;
        return searchNews(keyword, 1, 10);
    }

    /**
     * Generate news summary text for AI
     */
    public String generateNewsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 财经资讯速递\n\n");

        Map<String, List<Map<String, String>>> categorized = getNewsByCategory();
        for (Map.Entry<String, List<Map<String, String>>> entry : categorized.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n");
            for (Map<String, String> news : entry.getValue()) {
                sb.append("- ").append(news.get("title"));
                String source = news.get("source");
                if (StrUtil.isNotBlank(source)) {
                    sb.append(" (").append(source).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
