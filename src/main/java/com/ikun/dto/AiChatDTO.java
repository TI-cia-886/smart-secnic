package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 问答参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "AI 问答参数")
public class AiChatDTO {

    @Schema(description = "会话ID，为空时由服务端生成；多轮对话请回传同一 sessionId")
    private String sessionId;

    @Schema(description = "景区ID，用于优先匹配本景区知识")
    private Long scenicId;

    @NotBlank(message = "请输入您的问题")
    @Schema(description = "游客提问内容", example = "老人和儿童有优惠票吗")
    private String question;
}
