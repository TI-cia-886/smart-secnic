package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 工单转派参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单转派参数")
public class ComplaintAssignDTO {

    @NotNull(message = "工单ID不能为空")
    @Schema(description = "工单ID")
    private Long complaintId;

    @NotNull(message = "请选择处理人")
    @Schema(description = "处理人ID（sys_user.id）")
    private Long handlerId;

    @Schema(description = "转派说明")
    private String remark;
}
