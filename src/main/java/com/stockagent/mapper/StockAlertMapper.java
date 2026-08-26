package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.StockAlert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 股票预警Mapper
 */
@Mapper
public interface StockAlertMapper extends BaseMapper<StockAlert> {
}
