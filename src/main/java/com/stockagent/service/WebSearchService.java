package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web搜索服务 - 免费多源搜索
 * 数据源：新浪财经搜索 + 东方财富资讯
 */
@Service
@Slf4j
public class WebSearchService {

    private static final int DEFAULT_TIMEOUT = 10000;

    /** 用于从HTML中提取链接的正则，比纯字符串分割更健壮 */
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"(https?://[^\"]+)\"");
    private static final Pattern TITLE_PATTERN = Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.DOTALL);

    public List<Map<String, String>> search(String query, int maxResults) {
        if (StrUtil.isBlank(query)) return new ArrayList<>();
        maxResults = Math.min(maxResults, 20);

        List<Map<String, String>> results = new ArrayList<>();
        results.addAll(searchSina(query, maxResults));
        if (results.size() < maxResults) {
            results.addAll(searchEastmoney(query, maxResults - results.size()));
        }
        return results.subList(0, Math.min(results.size(), maxResults));
    }

    private List<Map<String, String>> searchSina(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://search.sina.com.cn/news?q=" + encodedQuery + "&c=news&sort=time&num=" + maxResults;
            String html = HttpUtil.get(url, DEFAULT_TIMEOUT);
            if (StrUtil.isBlank(html)) return results;

            // 使用正则提取<h2>块中的链接和标题，比字符串split更健壮
            String[] sections = html.split("<h2>");
            for (int i = 1; i < sections.length && results.size() < maxResults; i++) {
                String section = sections[i];
                try {
                    Matcher hrefMatcher = HREF_PATTERN.matcher(section);
                    Matcher titleMatcher = TITLE_PATTERN.matcher(section);
                    if (hrefMatcher.find() && titleMatcher.find()) {
                        String link = hrefMatcher.group(1);
                        String title = titleMatcher.group(1).replaceAll("<[^>]+>", "").trim();
                        if (StrUtil.isNotBlank(title) && title.length() > 5 && StrUtil.isNotBlank(link)) {
                            Map<String, String> item = new HashMap<>();
                            item.put("title", title); item.put("url", link); item.put("source", "新浪财经");
                            results.add(item);
                        }
                    }
                } catch (Exception e) {
                    log.debug("解析新浪搜索结果条目失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) { log.warn("新浪搜索失败: {}", e.getMessage()); }
        return results;
    }

    private List<Map<String, String>> searchEastmoney(String query, int maxResults) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://search-api-web.eastmoney.com/search/jsonp?cb=&type=8050&pageindex=1&pagesize=" + maxResults + "&keyword=" + encodedQuery;
            String response = HttpUtil.get(url, DEFAULT_TIMEOUT);
            if (StrUtil.isBlank(response)) return results;

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
                if (StrUtil.isNotBlank(r.get("title"))) results.add(r);
            }
        } catch (Exception e) { log.warn("东方财富搜索失败: {}", e.getMessage()); }
        return results;
    }
}
