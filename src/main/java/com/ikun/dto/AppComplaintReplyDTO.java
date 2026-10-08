package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序游客追加工单回复参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客追加工单回复参数")
public class AppComplaintReplyDTO {

    @NotBlank(message = "回复内容不能为空")
    @Schema(description = "回复内容")
    private String content;
}
