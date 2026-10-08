package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 服务概览视图对象（对应原型：AI 智能管理 → 顶部指标卡）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "AI 服务概览")
public class AiOverviewVO implements Serializable {

    @Schema(description = "累计调用次数")
    private Long totalCalls;

    @Schema(description = "调用成功次数")
    private Long successCalls;

    @Schema(description = "调用失败次数")
    private Long failCalls;

    @Schema(description = "调用成功率（百分比）")
    private BigDecimal successRate;

    @Schema(description = "平均响应耗时（毫秒）")
    private BigDecimal avgDuration;

    @Schema(description = "今日调用次数")
    private Long todayCalls;

    @Schema(description = "知识库条目总数")
    private Long knowledgeCount;

    @Schema(description = "已启用知识库条目数")
    private Long enabledKnowledgeCount;

    @Schema(description = "累计问答消息数")
    private Long chatCount;

    @Schema(description = "知识库命中率（命中问答 / 总问答，百分比）")
    private BigDecimal knowledgeHitRate;

    @Schema(description = "当前使用的 AI 实现标识")
    private String model;

    @Schema(description = "各功能模块调用分布")
    private List<StatItemVO> moduleDist = new ArrayList<>();

    @Schema(description = "近 7 天调用趋势")
    private List<TrendItemVO> callTrend = new ArrayList<>();
}
