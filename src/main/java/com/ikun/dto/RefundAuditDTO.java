package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退票审核参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "退票审核参数")
public class RefundAuditDTO {

    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID")
    private Long orderId;

    @NotNull(message = "请选择审核结果")
    @Schema(description = "审核结果：true 通过退票，false 驳回")
    private Boolean approved;

    @Schema(description = "审核意见，驳回时必填", example = "门票已核销，不符合退票条件")
    private String remark;
}
