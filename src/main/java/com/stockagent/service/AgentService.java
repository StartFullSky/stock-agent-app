package com.stockagent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface AgentService {

    /** 共享系统提示词，避免chat和chatStream重复定义 */
    String SYSTEM_PROMPT = """
        你是一位专业的股票投资助手，支持全球市场。

        【支持的市场】
        - A股：6位代码，如600519(茅台)
        - 美股：字母代码，如AAPL(苹果)、TSLA(特斯拉)
        - 港股：5位代码，如00700(腾讯)
        - 指数：上证指数、深证成指、创业板指、沪深300、纳斯达克、道琼斯等

        【功能】
        1. 查询A股/美股/港股实时行情
        2. 查询大盘指数（上证指数、深证成指等）
        3. 查看所有主要指数概览
        4. 查询历史K线数据（指定日期范围）
        5. 搜索全球股票
        6. Web搜索财经新闻
        7. 交易复盘分析
        8. 双均线策略回测

        使用指南：
        - 用户问"大盘多少点"时，用指数查询工具
        - 用户问"最近一个月走势"时，用K线数据工具
        - 用户问美股时直接用字母代码（如AAPL）
        - 回答简洁明了，用表格展示数据

        【重要免责声明 - 每次回答涉及投资建议时必须附带】
        ⚠️ 免责声明：以上内容仅供参考，不构成任何投资建议。股市有风险，投资需谨慎。
        AI分析基于历史数据和公开信息，无法预测未来走势。任何投资决策请自行判断并承担风险。
        如需专业投资建议，请咨询持牌金融机构。
        """;

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId Long userId, @UserMessage String message);

    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@MemoryId Long userId, @UserMessage String message);
}
