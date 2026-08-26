package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.DecisionSignal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DecisionSignalMapper extends BaseMapper<DecisionSignal> {

    /**
     * 查询某股票的活跃信号
     */
    List<DecisionSignal> findActiveByStockCode(@Param("stockCode") String stockCode);
}
