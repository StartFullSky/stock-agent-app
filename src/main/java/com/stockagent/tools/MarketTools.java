package com.stockagent.tools;

import cn.hutool.core.util.StrUtil;
import com.stockagent.service.StockService;
import com.stockagent.service.HithinkFinanceService;
import com.stockagent.service.SpecialDataService;
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
    private final HithinkFinanceService hithink;
    private final SpecialDataService specialData;

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

    @Tool("查询A股估值快照：市盈率(PE_TTM/PE_MRQ)、市净率(PB)、市销率(PS)、市现率(PCF)。输入股票代码或名称")
    public String getValuation(@P("股票代码或名称，如600519、贵州茅台") String stockCode) {
        log.info("查询估值: {}", stockCode);
        try {
            HithinkFinanceService.TickerInfo t = resolveA(stockCode);
            if (t == null) return "未找到该股票，请检查代码是否正确";
            Map<String, Object> v = specialData.valuation(t.thscode);
            if (v.isEmpty()) return "暂无估值数据";
            return String.format("【%s 估值】%s (%s)\nPE(TTM): %s | PE(MRQ): %s\nPB(MRQ): %s | PS(TTM): %s | PCF(TTM): %s",
                v.get("name"), t.name, t.thscode, fmt(v.get("peTtm")), fmt(v.get("peMrq")),
                fmt(v.get("pbMrq")), fmt(v.get("psTtm")), fmt(v.get("pcfTtm")));
        } catch (Exception e) {
            log.error("查询估值失败", e);
            return "查询估值暂时不可用，请稍后重试";
        }
    }

    @Tool("查询A股财务指标（成长/盈利/偿债/营运/现金流五类）。报告期格式：2025-1一季报、2025-2半年报、2025-3三季报、2025-4年报")
    public String getFinancialIndicators(@P("股票代码或名称，如600519") String stockCode, @P("报告期，如2025-3") String report) {
        log.info("查询财务指标: {} {}", stockCode, report);
        try {
            HithinkFinanceService.TickerInfo t = resolveA(stockCode);
            if (t == null) return "未找到该股票，请检查代码是否正确";
            String text = specialData.financialIndicatorsText(t.thscode, report);
            return text.isEmpty() ? "暂无该报告期财务数据" : String.format("【%s 财务指标】\n%s", t.name, text);
        } catch (Exception e) {
            log.error("查询财务指标失败", e);
            return "查询财务指标暂时不可用，请稍后重试";
        }
    }

    @Tool("今日涨停复盘：涨停股票池（含连板数、封单额、涨停原因、涨停时间）。日期可选，格式YYYY-MM-DD，空为今日")
    public String getLimitUpReview(@P("日期YYYY-MM-DD，可空表示今日") String date) {
        log.info("涨停复盘: {}", date);
        try {
            Map<String, Object> pool = specialData.limitUpPool(date, 50);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) pool.get("items");
            if (items.isEmpty()) return "暂无涨停数据（可能非交易日或数据未就绪）";
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("【涨停复盘】共 %s 只涨停，显示前 %d 只\n\n", pool.get("total"), Math.min(items.size(), 20)));
            for (int i = 0; i < Math.min(items.size(), 20); i++) {
                Map<String, Object> m = items.get(i);
                sb.append(String.format("%d. %s(%s) %s | 封单:%s | %s | %s\n", i + 1,
                    m.get("name"), m.get("thscode"), m.get("continueDayText"),
                    fmt(m.get("sealMoney")), m.get("limitUpTime"),
                    m.get("limitUpReason") == null ? "" : m.get("limitUpReason")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("涨停复盘失败", e);
            return "涨停复盘暂时不可用，请稍后重试";
        }
    }

    @Tool("A股热股榜Top30（市场关注度排行）。飙升榜=true 返回热度飙升榜")
    public String getHotStocks(@P("是否查飙升榜，默认false") boolean skyrocket) {
        log.info("热股榜: skyrocket={}", skyrocket);
        try {
            List<Map<String, Object>> items = specialData.hotStockList("day", skyrocket);
            if (items.isEmpty()) return "暂无热股榜数据";
            StringBuilder sb = new StringBuilder(skyrocket ? "【热度飙升榜 Top30】\n" : "【A股热股榜 Top30】\n");
            for (Map<String, Object> m : items) {
                sb.append(String.format("%s. %s(%s) 热度:%s\n", m.get("rank"), m.get("name"), m.get("thscode"), m.get("heat")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("热股榜失败", e);
            return "热股榜暂时不可用，请稍后重试";
        }
    }

    @Tool("查询个股异动原因（如涨停、大涨、大跌的官方解释）。输入股票代码或名称")
    public String getAnomalyReasons(@P("股票代码或名称，如600519") String stockCode) {
        log.info("异动原因: {}", stockCode);
        try {
            HithinkFinanceService.TickerInfo t = resolveA(stockCode);
            if (t == null) return "未找到该股票，请检查代码是否正确";
            List<Map<String, Object>> items = specialData.anomalyByStock(t.thscode);
            if (items.isEmpty()) return "近期无异动记录";
            StringBuilder sb = new StringBuilder(String.format("【%s 异动记录】\n", t.name));
            for (Map<String, Object> m : items) {
                sb.append(m.toString()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("异动原因失败", e);
            return "异动原因暂时不可用，请稍后重试";
        }
    }

    @Tool("龙虎榜查询（机构/游资席位上榜）。日期可选YYYY-MM-DD，类型：all全部/org机构榜/hot_money游资榜")
    public String getDragonTiger(@P("日期YYYY-MM-DD，可空表示最近交易日") String date, @P("榜单类型：all/org/hot_money") String boardType) {
        log.info("龙虎榜: {} {}", date, boardType);
        try {
            Map<String, Object> r = specialData.dragonTiger(date, boardType);
            if (r.isEmpty()) return "暂无龙虎榜数据";
            StringBuilder sb = new StringBuilder(String.format("【龙虎榜】%s (%s) 共%s只\n\n", r.get("tradeDate"), r.get("boardType"), r.get("stockCount")));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stocks = (List<Map<String, Object>>) r.get("stocks");
            for (int i = 0; i < Math.min(stocks.size(), 15); i++) {
                Map<String, Object> m = stocks.get(i);
                sb.append(String.format("%d. %s(%s) 净买额:%s\n", i + 1, m.get("name"), m.get("thscode"), m.get("net_value")));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("龙虎榜失败", e);
            return "龙虎榜暂时不可用，请稍后重试";
        }
    }

    /** 消歧为A股/指数标的（同花顺 meta 检索） */
    private HithinkFinanceService.TickerInfo resolveA(String codeOrName) {
        if (!hithink.isAvailable()) return null;
        HithinkFinanceService.TickerInfo t = hithink.resolveTicker(codeOrName, null);
        if (t == null || StrUtil.isBlank(t.thscode)) return null;
        if (!t.isAShare() && !t.isIndex()) return null;
        return t;
    }

    private String fmt(Object v) {
        return v == null ? "未披露" : v.toString();
    }

    private String detectMarket(String code) {
        if (code == null) return "A股";
        String c = code.toUpperCase().trim();
        if (c.matches("[A-Z]{1,5}")) return "美股";
        if (c.matches("\\d{5}")) return "港股";
        return "A股";
    }
}