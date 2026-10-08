package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 客流实时概览视图对象（对应原型：客流统计 → 顶部指标卡与实时走势）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "客流实时概览")
public class FlowOverviewVO implements Serializable {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "统计日期")
    private LocalDate statDate;

    @Schema(description = "当前在园人数")
    private Integer currentCount;

    @Schema(description = "今日累计入园人数")
    private Integer todayEnterCount;

    @Schema(description = "今日累计出园人数")
    private Integer todayLeaveCount;

    @Schema(description = "景区日承载量")
    private Integer capacity;

    @Schema(description = "承载力饱和度（百分比）")
    private BigDecimal saturationRate;

    @Schema(description = "预警级别：NORMAL 正常 / WARNING 预警 / DANGER 超载")
    private String warningLevel;

    @Schema(description = "异常景点数量（处于预警或超载状态）")
    private Integer warnSpotCount;

    @Schema(description = "各小时入园人数走势（0-23 时）")
    private List<TrendItemVO> hourlyTrend = new ArrayList<>();

    @Schema(description = "各小时在园人数走势（0-23 时，用于趋势图在园曲线）")
    private List<TrendItemVO> onlineTrend = new ArrayList<>();

    @Schema(description = "景点实时客流明细")
    private List<SpotFlowVO> spotFlows = new ArrayList<>();
}
