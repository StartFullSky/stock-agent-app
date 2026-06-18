package com.stockagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stockagent.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
