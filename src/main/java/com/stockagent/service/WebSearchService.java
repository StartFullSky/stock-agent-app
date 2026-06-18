package com.stockagent.service;
import lombok.extern.slf4j.Slf4j;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web搜索服务 - 免费多源搜索
 * 数据源：新浪财经搜索 + 百度资讯
 */
@Service
@Slf4j
public class WebSearchService {

    /**
     * 搜索财经新闻和资讯
     * @param query 搜索关键词
     * @param maxResults 最大结果数
     * @return 搜索结果列表
     */
    public List<Map<String, String>> search(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();

        // 1. 新浪财经搜索
        results.addAll(searchSina(query, maxResults));

        // 2. 如果结果不够，补充东方财富搜索
        if (results.size() < maxResults) {
            results.addAll(searchEastmoney(query, maxResults - results.size()));
        }

        return results.subList(0, Math.min(results.size(), maxResults));
    }

    /**
     * 新浪财经搜索
     */
    private List<Map<String, String>> searchSina(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://search.sina.com.cn/news?q=" + encodedQuery + "&c=news&sort=time&num=" + maxResults;

            String html = HttpUtil.get(url, 10000);
            if (StrUtil.isBlank(html)) return results;

            // 简单解析HTML提取标题和链接
            // 格式: <h2><a href="URL" target="_blank">TITLE</a></h2>
            String[] lines = html.split("<h2>");
            for (int i = 1; i < lines.length && results.size() < maxResults; i++) {
                String line = lines[i];
                try {
                    int hrefStart = line.indexOf("href=\"") + 6;
                    int hrefEnd = line.indexOf("\"", hrefStart);
                    String link = line.substring(hrefStart, hrefEnd);

                    int titleStart = line.indexOf(">", line.indexOf("<a")) + 1;
                    int titleEnd = line.indexOf("</a>", titleStart);
                    String title = line.substring(titleStart, titleEnd)
                            .replaceAll("<[^>]+>", "")  // 去除HTML标签
                            .trim();

                    if (StrUtil.isNotBlank(title) && title.length() > 5) {
                        Map<String, String> item = new HashMap<>();
                        item.put("title", title);
                        item.put("url", link);
                        item.put("source", "新浪财经");
                        results.add(item);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("新浪搜索失败: {}", e.getMessage());
        }
        return results;
    }

    /**
     * 东方财富资讯搜索
     */
    private List<Map<String, String>> searchEastmoney(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            // 东方财富7x24快讯搜索
            String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=&type=8050&pageindex=1&pagesize=" + maxResults + "&keyword=" + encodedQuery;

            String response = HttpUtil.get(url, 10000);
            if (StrUtil.isBlank(response)) return results;

            // 解析JSONP响应
            String json = response;
            if (json.startsWith("(")) json = json.substring(1);
            if (json.endsWith(")")) json = json.substring(0, json.length() - 1);

            JSONObject obj = JSONUtil.parseObj(json);
            JSONObject result = obj.getJSONObject("result");
            if (result == null) return results;

            JSONArray list = result.getJSONArray("list");
            if (list == null) return results;

            for (int i = 0; i < list.size() && results.size() < maxResults; i++) {
                JSONObject item = list.getJSONObject(i);
                Map<String, String> r = new HashMap<>();
                r.put("title", item.getStr("title", "").replaceAll("<[^>]+>", ""));
                r.put("url", item.getStr("url", ""));
                r.put("source", "东方财富");
                r.put("date", item.getStr("date", ""));
                if (StrUtil.isNotBlank(r.get("title"))) {
                    results.add(r);
                }
            }
        } catch (Exception e) {
            log.warn("东方财富搜索失败: {}", e.getMessage());
        }
        return results;
    }
}
