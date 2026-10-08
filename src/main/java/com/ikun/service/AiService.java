package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.AiChatDTO;
import com.ikun.dto.AiGenerateDTO;
import com.ikun.entity.AiChat;
import com.ikun.entity.AiKnowledge;
import com.ikun.entity.AiLog;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AiOverviewVO;

/**
 * AI 智能管理服务
 *
 * @author smart-scenic
 */
public interface AiService {

    PageResult<AiKnowledge> pageKnowledge(Integer pageNum, Integer pageSize, String keyword,
                                          Long scenicId, String category, Integer status);

    void saveKnowledge(AiKnowledge knowledge);

    void updateKnowledge(AiKnowledge knowledge);

    void removeKnowledge(Long id);

    PageResult<AiLog> pageLog(Integer pageNum, Integer pageSize, Long scenicId,
                              String module, String status);

    PageResult<AiChat> pageChat(Integer pageNum, Integer pageSize, Long scenicId,
                                String sessionId, Long touristId);

    AiOverviewVO overview(Long scenicId);

    /** 智能问答：优先命中知识库，未命中时由 AI 生成兜底 */
    AiChatReplyVO chat(AiChatDTO dto);

    /**
     * 生成内容并写入 AI 调用日志
     *
     * @return 生成的文本
     */
    String generate(AiGenerateDTO dto);
}
