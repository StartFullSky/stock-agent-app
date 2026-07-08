-- 自选股表
CREATE TABLE IF NOT EXISTS `user_watchlist` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `stock_name` VARCHAR(50) COMMENT '股票名称',
    `market` VARCHAR(10) DEFAULT 'A' COMMENT '市场: A/HK/US',
    `group_name` VARCHAR(50) DEFAULT '默认分组' COMMENT '分组名称',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code_group` (`stock_code`, `group_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自选股';

-- 持仓表
CREATE TABLE IF NOT EXISTS `user_portfolio` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stock_code` VARCHAR(20) NOT NULL COMMENT '股票代码',
    `stock_name` VARCHAR(50) COMMENT '股票名称',
    `market` VARCHAR(10) DEFAULT 'A' COMMENT '市场: A/HK/US',
    `status` VARCHAR(10) DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `close_time` DATETIME COMMENT '清仓时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持仓';

-- 交易记录表
CREATE TABLE IF NOT EXISTS `portfolio_trade` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `portfolio_id` BIGINT NOT NULL COMMENT '持仓ID',
    `trade_type` VARCHAR(10) NOT NULL COMMENT 'BUY/SELL',
    `price` DECIMAL(12,4) NOT NULL COMMENT '成交价',
    `quantity` INT NOT NULL COMMENT '数量',
    `trade_date` DATE NOT NULL COMMENT '交易日期',
    `fee` DECIMAL(10,2) DEFAULT 0 COMMENT '手续费',
    `note` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_portfolio` (`portfolio_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易记录';
