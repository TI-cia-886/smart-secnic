package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.AiLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface AiLogMapper extends BaseMapper<AiLog> {
}
