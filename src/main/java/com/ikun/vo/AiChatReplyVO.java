package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 问答回复视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "AI 问答回复")
public class AiChatReplyVO implements Serializable {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "回答内容")
    private String answer;

    @Schema(description = "答案来源：KNOWLEDGE 知识库命中 / GENERATED 模型生成")
    private String source;

    @Schema(description = "命中的知识库条目ID，未命中为空")
    private Long knowledgeId;

    @Schema(description = "命中的标准问题，未命中为空")
    private String matchedQuestion;

    @Schema(description = "本次应答耗时（毫秒）")
    private Integer duration;

    @Schema(description = "本次使用的 AI 实现标识")
    private String model;
}
