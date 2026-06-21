package com.stockagent.tools;

import com.stockagent.service.StockService;
import com.stockagent.dto.StockQuoteDTO;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketTools {

    private final StockService stockService;

    @Tool("查询股票实时行情，支持A股、美股、港股。A股用6位代码如600519，美股用字母代码如AAPL/TSLA，港股用5位代码如00700")
    public String getStockQuote(@P("股票代码，如600519、AAPL、TSLA、00700") String stockCode) {
        log.info("查询行情: {}", stockCode);
        try {
            StockQuoteDTO quote = stockService.getQuote(stockCode);
            if (quote == null || "未知".equals(quote.getStockName())) return "未找到该股票信息，请检查代码是否正确";
            String market = detectMarket(stockCode);
            return String.format("【%s 行情】\n股票: %s (%s)\n当前价格: %s\n涨跌额: %s\n涨跌幅: %s%%\n开盘: %s | 最高: %s | 最低: %s\n成交量: %d",
                market, quote.getStockName(), quote.getStockCode(), quote.getCurrentPrice(),
                quote.getChangeAmount(), quote.getChangeRate(), quote.getOpenPrice(),
                quote.getHighPrice(), quote.getLowPrice(), quote.getVolume());
        } catch (Exception e) {
            log.error("查询行情失败", e);
            return "查询行情暂时不可用，请稍后重试";
        }
    }

    @Tool("查询大盘指数行情。可查询：上证指数、深证成指、创业板指、沪深300、上证50、中证500、纳斯达克、道琼斯、标普500、恒生指数等")
    public String getIndexQuote(@P("指数名称，如'上证指数'、'沪深300'、'纳斯达克'") String indexName) {
        log.info("查询指数: {}", indexName);
        try {
            StockQuoteDTO quote = stockService.getQuote(indexName);
            if (quote == null || "未知".equals(quote.getStockName())) return "未找到该指数，请检查名称是否正确";
            return String.format("【%s】\n当前点位: %s\n涨跌: %s\n涨跌幅: %s%%\n今日最高: %s\n今日最低: %s",
                indexName, quote.getCurrentPrice(), quote.getChangeAmount(), quote.getChangeRate(),
                quote.getHighPrice(), quote.getLowPrice());
        } catch (Exception e) {
            log.error("查询指数失败", e);
            return "查询指数暂时不可用，请稍后重试";
        }
    }

    @Tool("获取所有主要指数行情概览，包括上证指数、深证成指、创业板指、沪深300等")
    public String getAllIndices() {
        log.info("查询所有指数");
        try {
            List<Map<String, Object>> indices = stockService.getMainIndices();
            if (indices.isEmpty()) return "获取指数数据失败";
            StringBuilder sb = new StringBuilder("【主要指数行情】\n\n");
            for (Map<String, Object> idx : indices) {
                Object rate = idx.get("changeRate");
                String arrow = "➡️";
                if (rate != null) {
                    try {
                        if (Double.parseDouble(rate.toString()) > 0) arrow = "🔴";
                        else if (Double.parseDouble(rate.toString()) < 0) arrow = "🟢";
                    } catch (Exception ignored) {}
                }
                sb.append(String.format("%s %s: %s  涨跌幅: %s%%\n", arrow, idx.get("name"), idx.get("price"), rate));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("获取指数失败", e);
            return "获取指数数据暂时不可用，请稍后重试";
        }
    }

    @Tool("查询历史K线数据。输入股票代码、开始日期、结束日期，返回每日开盘价、收盘价、最高价、最低价")
    public String getKlineData(@P("股票代码，如600519") String stockCode, @P("开始日期，格式YYYY-MM-DD") String startDate, @P("结束日期，格式YYYY-MM-DD") String endDate) {
        log.info("查询K线: {} {} ~ {}", stockCode, startDate, endDate);
        try {
            List<Map<String, Object>> klines = stockService.getKlineData(stockCode, startDate, endDate, 100);
            if (klines.isEmpty()) return "未找到K线数据";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【%s 历史K线】\n\n", stockCode));
            sb.append("日期       | 开盘   | 收盘    | 最高   | 最低\n");
            sb.append("-----------|---------|---------|---------|--------\n");
            int show = Math.min(klines.size(), 20);
            for (int i = klines.size() - show; i < klines.size(); i++) {
                Map<String, Object> k = klines.get(i);
                sb.append(String.format("%s | %-7s | %-7s | %-7s | %s\n",
                    k.get("date"), k.get("open"), k.get("close"), k.get("high"), k.get("low")));
            }
            if (klines.size() > show) sb.append(String.format("\n... 共%d 条数据，仅显示最近%d 条", klines.size(), show));
            return sb.toString();
        } catch (Exception e) {
            log.error("获取K线失败", e);
            return "获取K线数据暂时不可用，请稍后重试";
        }
    }

    @Tool("搜索全球股票，输入关键词可搜A股（中文名/代码）、美股（英文代码如AAPL）、港股（数字代码如00700）、指数（如上证指数）")
    public String searchStock(@P("搜索关键词，如'茅台'(A股)、'AAPL'(美股)、'00700'(港股)、'上证指数'(指数)") String keyword) {
        log.info("搜索股票: {}", keyword);
        try {
            List<Map<String, String>> results = stockService.searchGlobalStock(keyword);
            if (results.isEmpty()) return "未找到相关股票";
            StringBuilder sb = new StringBuilder("搜索结果：\n");
            for (int i = 0; i < Math.min(results.size(), 10); i++) {
                Map<String, String> r = results.get(i);
                sb.append(String.format("%d. %s - %s [%s]\n", i + 1, r.get("name"), r.get("code"), r.get("market")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("搜索股票失败", e);
            return "搜索暂时不可用，请稍后重试";
        }
    }

    private String detectMarket(String code) {
        if (code == null) return "A股";
        String c = code.toUpperCase().trim();
        if (c.matches("[A-Z]{1,5}")) return "美股";
        if (c.matches("\\d{5}")) return "港股";
        return "A股";
    }
}