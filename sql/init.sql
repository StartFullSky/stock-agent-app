USE stock_agent;

-- 1. 股票信息表
CREATE TABLE IF NOT EXISTS stock_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50) NOT NULL,
    market VARCHAR(10) NOT NULL,
    industry VARCHAR(50),
    status INT DEFAULT 1,
    INDEX idx_stock_code (stock_code),
    INDEX idx_market (market)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 聊天历史表
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 0,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 股票行情缓存表
CREATE TABLE IF NOT EXISTS stock_quote_cache (
    stock_code VARCHAR(20) PRIMARY KEY,
    stock_name VARCHAR(50),
    current_price DECIMAL(10,2),
    open_price DECIMAL(10,2),
    high_price DECIMAL(10,2),
    low_price DECIMAL(10,2),
    pre_close DECIMAL(10,2),
    change_amount DECIMAL(10,2),
    change_rate DECIMAL(10,4),
    volume BIGINT,
    amount DECIMAL(18,2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 股票财务数据表
CREATE TABLE IF NOT EXISTS stock_financial (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    report_date DATE,
    report_type VARCHAR(20),
    revenue DECIMAL(18,2),
    net_profit DECIMAL(18,2),
    pe_ratio DECIMAL(10,2),
    pb_ratio DECIMAL(10,2),
    roe DECIMAL(10,4),
    debt_ratio DECIMAL(10,4),
    gross_margin DECIMAL(10,4),
    INDEX idx_stock_code (stock_code),
    INDEX idx_report_date (report_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 交易记录表
CREATE TABLE IF NOT EXISTS stock_trade (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 0,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    trade_type INT NOT NULL COMMENT '1=买入, 2=卖出',
    quantity INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    amount DECIMAL(14,2),
    fee DECIMAL(10,2),
    trade_date DATE,
    remark VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_stock_code (stock_code),
    INDEX idx_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 用户自选股表
CREATE TABLE IF NOT EXISTS user_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL DEFAULT 0,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(50),
    market VARCHAR(10),
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_stock (user_id, stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入股票基础数据
INSERT IGNORE INTO stock_info (stock_code, stock_name, market, industry) VALUES
('600519','贵州茅台','SH','白酒'),('601318','中国平安','SH','保险'),('600036','招商银行','SH','银行'),
('000858','五粮液','SZ','白酒'),('600276','恒瑞医药','SH','医药'),('300750','宁德时代','SZ','电池'),
('000333','美的集团','SZ','家电'),('600900','长江电力','SH','电力'),('601398','工商银行','SH','银行'),
('600030','中信证券','SH','证券'),('000001','平安银行','SZ','银行'),('002415','海康威视','SZ','安防'),
('600809','山西汾酒','SH','白酒'),('000568','泸州老窖','SZ','白酒'),('300059','东方财富','SZ','证券'),
('601888','中国中免','SH','零售'),('002594','比亚迪','SZ','汽车'),('601899','紫金矿业','SH','矿业'),
('000725','京东方A','SZ','面板'),('002475','立讯精密','SZ','电子'),('600887','伊利股份','SH','乳业'),
('300015','爱尔眼科','SZ','医疗'),('601857','中国石油','SH','石油'),('601088','中国神华','SH','煤炭'),
('000002','万科A','SZ','地产'),('600048','保利发展','SH','地产'),('603259','药明康德','SH','医药'),
('300760','迈瑞医疗','SZ','医疗'),('688981','中芯国际','SH','半导体'),('002049','紫光国微','SZ','半导体'),
('300274','阳光电源','SZ','光伏'),('600438','通威股份','SH','光伏'),('002352','顺丰控股','SZ','物流'),
('601166','兴业银行','SH','银行'),('000651','格力电器','SZ','家电'),('300014','亿纬锂能','SZ','电池'),
('600585','海螺水泥','SH','建材'),('601225','陕西煤业','SH','煤炭'),('002241','歌尔股份','SZ','电子'),
('603501','韦尔股份','SH','半导体'),('601012','隆基绿能','SH','光伏'),('600050','中国联通','SH','通信'),
('601728','中国电信','SH','通信'),('002714','牧原股份','SZ','养殖'),('002304','洋河股份','SZ','白酒'),
('300661','圣邦股份','SZ','半导体'),('600089','特变电工','SH','电力设备'),('600028','中国石化','SH','石油');
