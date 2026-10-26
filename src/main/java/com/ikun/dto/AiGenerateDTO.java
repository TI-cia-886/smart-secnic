package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 内容生成参数（公告草稿 / 客流疏导预案）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "AI 内容生成参数")
public class AiGenerateDTO {

    // 场景由 Controller 按接口强制覆盖（announcement/draft -> ANNOUNCEMENT、flow-plan -> FLOW_PLAN），
    // 前端无需传入；若加 @NotBlank，@Valid 会在 setScene 之前拦截请求导致无法生成
    @Schema(description = "场景：ANNOUNCEMENT 公告草稿 / FLOW_PLAN 客流疏导预案（由接口指定，前端无需传入）",
            example = "ANNOUNCEMENT")
    private String scene;

    @Schema(description = "主题或补充要求，公告场景下为主题",
            example = "国庆假期东门区域临时管控")
    private String prompt;

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景点ID，疏导预案可指定具体景点")
    private Long spotId;

    @Schema(description = "预警记录ID，传入时自动带入该预警的实时人数与承载量")
    private Long warningId;

    @Schema(description = "生效开始时间描述", example = "10月1日 08:00")
    private String beginTime;

    @Schema(description = "生效结束时间描述", example = "10月7日 18:00")
    private String endTime;
}
