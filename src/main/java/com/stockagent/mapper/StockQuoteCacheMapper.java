package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.StockQuoteCache;
import org.apache.ibatis.annotations.Mapper;

/**
 * 行情缓存Mapper
 */
@Mapper
public interface StockQuoteCacheMapper extends BaseMapper<StockQuoteCache> {
}
