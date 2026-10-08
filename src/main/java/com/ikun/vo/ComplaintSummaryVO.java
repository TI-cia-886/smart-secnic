package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工单批量归纳汇总视图对象（对应原型：投诉工单管理 → 批量归纳汇总）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单归纳汇总")
public class ComplaintSummaryVO implements Serializable {

    @Schema(description = "汇总工单总数")
    private Integer total;

    @Schema(description = "负面情绪工单数")
    private Integer negativeCount;

    @Schema(description = "紧急工单数")
    private Integer urgentCount;

    @Schema(description = "待处理工单数")
    private Integer pendingCount;

    @Schema(description = "工单类型分布")
    private List<StatItemVO> typeDist = new ArrayList<>();

    @Schema(description = "情感倾向分布")
    private List<StatItemVO> sentimentDist = new ArrayList<>();

    @Schema(description = "高频问题关键词 Top N")
    private List<StatItemVO> topKeywords = new ArrayList<>();

    @Schema(description = "AI 生成的归纳文字")
    private String summaryText;

    @Schema(description = "本次归纳使用的模型标识")
    private String model;
}
