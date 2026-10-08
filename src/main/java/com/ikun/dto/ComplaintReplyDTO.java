package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工单回复参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单回复参数")
public class ComplaintReplyDTO {

    @NotBlank(message = "回复内容不能为空")
    @Schema(description = "回复内容")
    private String content;

    @Schema(description = "回复后是否直接完结工单", example = "false")
    private Boolean closeAfterReply;
}
