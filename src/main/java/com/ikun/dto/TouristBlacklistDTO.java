package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 游客黑名单设置参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客黑名单设置参数")
public class TouristBlacklistDTO {

    @NotNull(message = "游客ID不能为空")
    @Schema(description = "游客ID")
    private Long touristId;

    @NotBlank(message = "请填写加入黑名单的原因")
    @Schema(description = "黑名单原因，必填以便后续复核", example = "多次恶意占位不支付")
    private String reason;
}
