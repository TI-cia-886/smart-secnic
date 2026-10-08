package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 工单批量归纳参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单批量归纳参数")
public class ComplaintSummaryDTO {

    @Schema(description = "要归纳的工单ID集合；为空时归纳当前景区待处理工单（最多 100 条）")
    private List<Long> ids;
}
