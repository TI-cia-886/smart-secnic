package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.Tourist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游客 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface TouristMapper extends BaseMapper<Tourist> {
}
