package com.stockagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stockagent.entity.Portfolio;
import com.stockagent.entity.PortfolioTrade;
import com.stockagent.mapper.PortfolioMapper;
import com.stockagent.mapper.PortfolioTradeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioMapper portfolioMapper;
    private final PortfolioTradeMapper tradeMapper;

    private static final String TENCENT_API_URL = "http://qt.gtimg.cn/q=";

    /**
     * List all open portfolios with P&L
     */
    public List<Map<String, Object>> listOpen() {
        List<Portfolio> portfolios = portfolioMapper.selectList(
            new LambdaQueryWrapper<Portfolio>()
                .eq(Portfolio::getStatus, "OPEN")
                .orderByDesc(Portfolio::getCreateTime)
        );
        return portfolios.stream().map(this::calcPnl).collect(Collectors.toList());
    }

    /**
     * List closed portfolios
     */
    public List<Map<String, Object>> listClosed() {
        List<Portfolio> portfolios = portfolioMapper.selectList(
            new LambdaQueryWrapper<Portfolio>()
                .eq(Portfolio::getStatus, "CLOSED")
                .orderByDesc(Portfolio::getCloseTime)
        );
        return portfolios.stream().map(this::calcPnl).collect(Collectors.toList());
    }

    /**
     * Create a new portfolio entry and add first buy trade
     */
    @Transactional
    public Portfolio create(String stockCode, String stockName, String market,
                           BigDecimal price, Integer quantity, LocalDate tradeDate, BigDecimal fee) {
        Portfolio p = new Portfolio();
        p.setStockCode(stockCode.toUpperCase());
        p.setStockName(stockName);
        p.setMarket(market == null ? "A" : market);
        p.setStatus("OPEN");
        p.setCreateTime(LocalDateTime.now());
        portfolioMapper.insert(p);

        // First buy trade
        PortfolioTrade trade = new PortfolioTrade();
        trade.setPortfolioId(p.getId());
        trade.setTradeType("BUY");
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setTradeDate(tradeDate == null ? LocalDate.now() : tradeDate);
        trade.setFee(fee == null ? BigDecimal.ZERO : fee);
        trade.setCreateTime(LocalDateTime.now());
        tradeMapper.insert(trade);

        return p;
    }

    /**
     * Add a trade to existing portfolio
     */
    @Transactional
    public void addTrade(Long portfolioId, String tradeType, BigDecimal price,
                         Integer quantity, LocalDate tradeDate, BigDecimal fee, String note) {
        Portfolio p = portfolioMapper.selectById(portfolioId);
        if (p == null) throw new IllegalArgumentException("Portfolio not found");

        PortfolioTrade trade = new PortfolioTrade();
        trade.setPortfolioId(portfolioId);
        trade.setTradeType(tradeType.toUpperCase());
        trade.setPrice(price);
        trade.setQuantity(quantity);
        trade.setTradeDate(tradeDate == null ? LocalDate.now() : tradeDate);
        trade.setFee(fee == null ? BigDecimal.ZERO : fee);
        trade.setNote(note);
        trade.setCreateTime(LocalDateTime.now());
        tradeMapper.insert(trade);

        // Check if all sold
        if ("SELL".equalsIgnoreCase(tradeType)) {
            int totalBuy = getTotalQuantity(portfolioId, "BUY");
            int totalSell = getTotalQuantity(portfolioId, "SELL");
            if (totalSell >= totalBuy) {
                p.setStatus("CLOSED");
                p.setCloseTime(LocalDateTime.now());
                portfolioMapper.updateById(p);
            }
        }
    }

    /**
     * Get trade history for a portfolio
     */
    public List<PortfolioTrade> getTrades(Long portfolioId) {
        return tradeMapper.selectList(
            new LambdaQueryWrapper<PortfolioTrade>()
                .eq(PortfolioTrade::getPortfolioId, portfolioId)
                .orderByAsc(PortfolioTrade::getTradeDate)
        );
    }

    /**
     * Delete portfolio and its trades
     */
    @Transactional
    public void delete(Long id) {
        tradeMapper.delete(new LambdaQueryWrapper<PortfolioTrade>()
            .eq(PortfolioTrade::getPortfolioId, id));
        portfolioMapper.deleteById(id);
    }

    /**
     * Calculate P&L for a portfolio
     */
    private Map<String, Object> calcPnl(Portfolio p) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", p.getId());
        result.put("stockCode", p.getStockCode());
        result.put("stockName", p.getStockName());
        result.put("market", p.getMarket());
        result.put("status", p.getStatus());
        result.put("createTime", p.getCreateTime());
        result.put("closeTime", p.getCloseTime());

        List<PortfolioTrade> trades = getTrades(p.getId());
        result.put("trades", trades);

        int totalBuyQty = getTotalQuantity(p.getId(), "BUY");
        int totalSellQty = getTotalQuantity(p.getId(), "SELL");
        BigDecimal totalBuyCost = getTotalCost(p.getId(), "BUY");
        BigDecimal totalSellRevenue = getTotalCost(p.getId(), "SELL");
        BigDecimal totalFee = trades.stream()
            .map(t -> t.getFee() == null ? BigDecimal.ZERO : t.getFee())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgBuyPrice = totalBuyQty > 0
            ? totalBuyCost.divide(BigDecimal.valueOf(totalBuyQty), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        int holdingQty = totalBuyQty - totalSellQty;
        result.put("totalBuyQty", totalBuyQty);
        result.put("totalSellQty", totalSellQty);
        result.put("holdingQty", holdingQty);
        result.put("avgBuyPrice", avgBuyPrice);
        result.put("totalBuyCost", totalBuyCost);
        result.put("totalSellRevenue", totalSellRevenue);
        result.put("totalFee", totalFee);

        if ("OPEN".equals(p.getStatus()) && holdingQty > 0) {
            // Try to get current price
            BigDecimal currentPrice = fetchCurrentPrice(p.getStockCode(), p.getMarket());
            if (currentPrice != null) {
                BigDecimal marketValue = currentPrice.multiply(BigDecimal.valueOf(holdingQty));
                BigDecimal unrealizedPnl = marketValue.subtract(
                    totalBuyCost.subtract(totalSellRevenue)
                );
                result.put("currentPrice", currentPrice);
                result.put("marketValue", marketValue);
                result.put("unrealizedPnl", unrealizedPnl);
                if (totalBuyCost.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal pnlRate = unrealizedPnl.multiply(BigDecimal.valueOf(100))
                        .divide(totalBuyCost, 2, RoundingMode.HALF_UP);
                    result.put("pnlRate", pnlRate);
                }
            }
        } else {
            // Closed position
            BigDecimal realizedPnl = totalSellRevenue.subtract(totalBuyCost).subtract(totalFee);
            result.put("realizedPnl", realizedPnl);
            if (totalBuyCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pnlRate = realizedPnl.multiply(BigDecimal.valueOf(100))
                    .divide(totalBuyCost, 2, RoundingMode.HALF_UP);
                result.put("pnlRate", pnlRate);
            }
        }

        return result;
    }

    private int getTotalQuantity(Long portfolioId, String tradeType) {
        List<PortfolioTrade> trades = tradeMapper.selectList(
            new LambdaQueryWrapper<PortfolioTrade>()
                .eq(PortfolioTrade::getPortfolioId, portfolioId)
                .eq(PortfolioTrade::getTradeType, tradeType)
        );
        return trades.stream().mapToInt(PortfolioTrade::getQuantity).sum();
    }

    private BigDecimal getTotalCost(Long portfolioId, String tradeType) {
        List<PortfolioTrade> trades = tradeMapper.selectList(
            new LambdaQueryWrapper<PortfolioTrade>()
                .eq(PortfolioTrade::getPortfolioId, portfolioId)
                .eq(PortfolioTrade::getTradeType, tradeType)
        );
        return trades.stream()
            .map(t -> t.getPrice().multiply(BigDecimal.valueOf(t.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal fetchCurrentPrice(String stockCode, String market) {
        try {
            String prefix = "HK".equals(market) ? "hk" : "US".equals(market) ? "us" : "s_";
            String code = prefix + stockCode.toLowerCase();
            String resp = HttpUtil.get(TENCENT_API_URL + code, 3000);
            // Parse simple quote
            if (resp.contains("~")) {
                String[] parts = resp.split("~");
                if (parts.length >= 4) {
                    return new BigDecimal(parts[3]);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch price for {}: {}", stockCode, e.getMessage());
        }
        return null;
    }

    /**
     * Get summary stats
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        List<Map<String, Object>> openList = listOpen();
        List<Map<String, Object>> closedList = listClosed();

        BigDecimal totalUnrealized = openList.stream()
            .map(m -> (BigDecimal) m.getOrDefault("unrealizedPnl", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRealized = closedList.stream()
            .map(m -> (BigDecimal) m.getOrDefault("realizedPnl", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMarketValue = openList.stream()
            .map(m -> (BigDecimal) m.getOrDefault("marketValue", BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.put("openCount", openList.size());
        summary.put("closedCount", closedList.size());
        summary.put("totalMarketValue", totalMarketValue);
        summary.put("totalUnrealizedPnl", totalUnrealized);
        summary.put("totalRealizedPnl", totalRealized);
        return summary;
    }
}
