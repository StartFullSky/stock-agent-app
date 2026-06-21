package com.stockagent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSearchService 单元测试
 * 注意：这些测试依赖外部API，标记为集成测试
 */
class WebSearchServiceTest {

    private final WebSearchService webSearchService = new WebSearchService();

    @Test
    @DisplayName("空查询应返回空结果")
    void shouldReturnEmptyForBlankQuery() {
        List<Map<String, String>> results = webSearchService.search("", 5);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("null查询应返回空结果")
    void shouldReturnEmptyForNullQuery() {
        List<Map<String, String>> results = webSearchService.search(null, 5);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("maxResults应被限制在20以内")
    void shouldLimitMaxResults() {
        // 不会抛异常，验证参数被正确限制
        List<Map<String, String>> results = webSearchService.search("股票", 100);
        assertNotNull(results);
        assertTrue(results.size() <= 20);
    }
}
