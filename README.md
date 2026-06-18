# Stock Agent - AI股票投资助手

基于 Java + Spring Boot + LangChain4j + 小米MiMo大模型 的智能股票投资助手，支持自然语言交互查询全球股票行情、财经新闻、交易复盘和策略回测。

## ✨ 功能特性

- 🌍 **全球市场** — A股/美股/港股/大盘指数实时行情
- 📰 **财经新闻** — Web搜索获取最新财经资讯
- 📊 **历史K线** — 查询指定日期范围的历史走势
- 📋 **交易复盘** — 分析交易行为、胜率、盈亏诊断
- 📈 **策略回测** — 双均线策略历史回测验证
- 🔍 **股票搜索** — 中文名搜A股、英文代码搜美股、数字搜港股
- 🤖 **AI对话** — 自然语言交互，AI自动调用合适工具

## 🛠️ 技术栈

| 技术 | 说明 |
|------|------|
| Java 17 | 开发语言 |
| Spring Boot 3.2.5 | 框架 |
| LangChain4j 0.35.0 | AI Agent框架 |
| 小米 MiMo v2.5 Pro | 大语言模型 |
| MyBatis-Plus 3.5.6 | ORM框架 |
| MySQL 8.0 | 数据库 |
| Redis | 缓存 |
| Knife4j | API文档 |
| Hutool | 工具库 |

## 🚀 快速开始

### 1. 环境要求

- JDK 17+
- MySQL 8.0+
- Redis
- Maven 3.6+

### 2. 数据库准备

```sql
CREATE DATABASE stock_agent DEFAULT CHARACTER SET utf8mb4;
```

### 3. 配置修改

编辑 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/stock_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password

ai:
  api-key: your_mimo_api_key
  model: mimo-v2.5-pro
  base-url: https://token-plan-cn.xiaomimimo.com/v1
```

### 4. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/stock-agent-app-1.0.0.jar
```

或在 IDEA 中直接运行 `StockAgentApp.java`

### 5. 访问

| 地址 | 说明 |
|------|------|
| http://localhost:9090 | 前端聊天页面 |
| http://localhost:9090/doc.html | API文档 |

## 📡 API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | POST | AI对话 |
| `/api/chat/test?msg=你好` | GET | 测试对话 |
| `/api/stock/quote/{code}` | GET | 实时行情 |
| `/api/stock/indices` | GET | 主要指数 |
| `/api/stock/kline` | GET | 历史K线 |
| `/api/stock/global-search` | GET | 全球搜索 |
| `/api/search` | GET | Web搜索 |
| `/api/trade-review` | GET | 交易复盘 |
| `/api/backtest/ma` | GET | 策略回测 |

## 📁 项目结构

```
src/main/java/com/stockagent/
├── StockAgentApp.java          # 启动类
├── config/                     # 配置
│   ├── AiConfig.java           # AI模型配置
│   └── WebConfig.java          # Web配置
├── controller/                 # 控制器
│   ├── ChatController.java     # AI对话
│   └── DataController.java     # 数据接口
├── dto/                        # 数据传输对象
├── entity/                     # 实体类
├── mapper/                     # MyBatis Mapper
├── service/                    # 服务层
│   ├── AgentService.java       # AI Agent服务
│   ├── StockService.java       # 行情服务
│   ├── WebSearchService.java   # Web搜索
│   ├── TradeReviewService.java # 交易复盘
│   └── BacktestService.java    # 策略回测
└── tools/                      # AI工具
    ├── MarketTools.java        # 行情工具
    └── AgentTools.java         # 搜索/复盘/回测工具
```

## 📄 License

MIT License
