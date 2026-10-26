package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 500, message = "回复内容不能超过 500 个字符")
    @Schema(description = "回复内容")
    private String content;
}
