package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.StockTrade;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockTradeMapper extends BaseMapper<StockTrade> {
}
