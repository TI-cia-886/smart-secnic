package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 小程序申请退票参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "申请退票参数")
public class AppRefundDTO {

    @NotBlank(message = "请填写退票原因")
    @Schema(description = "退票原因", example = "行程变更，无法按时前往")
    private String reason;
}
