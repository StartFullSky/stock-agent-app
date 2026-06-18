package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
