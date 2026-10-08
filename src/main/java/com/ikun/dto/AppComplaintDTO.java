package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 小程序提交工单参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "提交工单参数")
public class AppComplaintDTO {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "关联订单号，选填")
    private String orderNo;

    @NotBlank(message = "请填写问题标题")
    @Schema(description = "问题标题", example = "东门排队时间过长")
    private String title;

    @NotBlank(message = "请描述您遇到的问题")
    @Schema(description = "问题描述")
    private String content;

    @Schema(description = "类型：COMPLAINT 投诉 / SUGGESTION 建议 / CONSULT 咨询", example = "COMPLAINT")
    private String type;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系电话，不填则取账号绑定手机号")
    private String phone;
}
