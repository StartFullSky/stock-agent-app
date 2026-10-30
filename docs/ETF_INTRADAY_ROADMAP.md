# ETF Intraday Quant System — Implementation Roadmap

## User-selected operating mode

- Universe: ETFs first; do not mix ordinary equities into the first strategy.
- Decision mode: automatic scanning and analysis, human approval required.
- Holding horizon: intraday.
- Order execution: disabled in this phase. No broker credentials or order endpoint should be added until paper-trading validation and an explicit later approval.

## Current repository responsibilities

- `fund-monitor`: market-data adapters, scheduled monitoring, notifications, watchlists, and lightweight Python signal calculations.
- `stock-agent-app`: Java API, dashboard, persisted decision signals, portfolio views, AI explanation, and future backtest/reporting endpoints.

Keep signal generation deterministic. Use the AI layer to explain evidence and risks, not to invent prices or independently authorize orders.

## First-pass audit findings

1. `fund-monitor/scripts/stock_analysis.py` previously calculated RSI by averaging only positive and negative observations separately, and approximated MACD with simple moving averages. This has been corrected to Wilder RSI and EMA-based MACD.
2. `fund-monitor/scripts/stock_utils.py` previously embedded a weak database password fallback and used the host machine's local timezone for A-share session checks. Configuration is now environment-driven and session checks use Asia/Shanghai.
3. `get_stock_realtime` may fall back to daily AKShare data when the real-time quote endpoint fails. The fallback is now explicitly marked `is_realtime=False`; intraday alert processing skips it.
4. `stock-agent-app/src/main/java/com/stockagent/service/BacktestService.java` currently executes signals at the same bar's close used to calculate them. This is optimistic and can introduce look-ahead bias. It also buys arbitrary share counts, omits fees/slippage, and does not model ETF-specific lot size, T+0/T+1 eligibility, suspensions, price limits, or tradability.
5. `DecisionSignalService` defaults signal expiry to 30 days, which is too long for intraday candidates. Intraday signals need short expiry, explicit approval/decline/expire states, and a one-time approval token before any future execution integration.

## Intraday candidate engine added

`fund-monitor/scripts/intraday_signal.py` computes a conservative candidate from completed intraday bars:
- EMA(9)/EMA(21) trend alignment
- Session VWAP
- Wilder RSI(14)
- Current-bar volume relative to prior bars
- ATR-based stop/target reference levels

It returns `HOLD`, `BUY_CANDIDATE`, or `SELL_CANDIDATE`; it never sends an order. The output explicitly requires human confirmation and disables execution. It rejects missing, non-finite, out-of-order, or internally inconsistent OHLCV input.

The current rule is a baseline for research, not a validated profitable strategy. Thresholds must be selected with walk-forward testing and transaction-cost sensitivity analysis.

## Next implementation stages

### Stage 1 — data correctness and observability
- Add ETF metadata registry: code, exchange, product type, trading session, lot size, T+0/T+1 rule, tick size, limit rules, and source.
- Require quote timestamp, source, latency, and real-time flag for every bar/quote.
- Reject stale data and crossed/inconsistent quotes; never silently substitute daily bars for intraday bars.
- Normalize all timestamps to Asia/Shanghai and explicitly handle lunch break, holidays, and early close.

### Stage 2 — strategy and risk engine
- Keep completed 1-minute bars as the initial signal frequency.
- Add spread/liquidity filters and no-trade windows around open/close.
- Add per-trade risk, daily loss cap, max trades/day, cooldown, and kill switch.
- Enforce ETF-specific settlement/tradability rules. T+0 is product-dependent; never assume every ETF can be sold on the same day.
- Signal expiry should be minutes, not days.

### Stage 3 — backtest and paper trading
- Execute a signal no earlier than the next tradable bar (or next executable quote).
- Model commissions, minimum commission, slippage, spread, tick size, lot size, and product-specific settlement.
- Report net return, max drawdown, Sharpe/Sortino, profit factor, turnover, trade count, exposure, and benchmark comparison.
- Use chronological train/validation/test splits and walk-forward evaluation; include out-of-sample and stress periods.
- Run a paper-trading shadow period before any live execution.

### Stage 4 — human approval workflow
- Persist each candidate with data timestamp, strategy version, evidence, entry reference, invalidation, risk amount, expiry, and ETF rule profile.
- Present approve/reject/ignore actions in the dashboard; approvals expire and cannot be replayed.
- The system must revalidate price, spread, data freshness, position, and risk limits at approval time.
- Order execution remains off until a separate explicit decision and broker-specific compliance review.

## Acceptance criteria before any real-money use

- Unit/integration tests pass; data outage and stale-feed tests pass.
- Backtest uses next-bar execution and realistic fees/slippage.
- Out-of-sample results are stable and not dependent on one ETF or one market regime.
- Paper trading reconciles every signal, approval, order simulation, and fill.
- Daily loss cap and global kill switch are tested.
- No model-generated text can bypass deterministic risk checks or human approval.
