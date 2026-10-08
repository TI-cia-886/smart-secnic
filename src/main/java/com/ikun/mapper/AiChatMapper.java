package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.AiChat;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话记录 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface AiChatMapper extends BaseMapper<AiChat> {
}
