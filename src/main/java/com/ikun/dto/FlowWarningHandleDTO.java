package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 客流预警处理参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "客流预警处理参数")
public class FlowWarningHandleDTO {

    @NotNull(message = "预警记录ID不能为空")
    @Schema(description = "预警记录ID")
    private Long warningId;

    @NotBlank(message = "请填写处置说明")
    @Schema(description = "处置说明，如「已启动分时预约限流，东门临时封闭」",
            example = "已启动分时预约限流，东门临时封闭 30 分钟")
    private String handleRemark;
}
