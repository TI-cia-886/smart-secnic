package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 客流数据报表视图对象（对应原型：客流统计 → 客流数据报表）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "客流数据报表")
public class FlowReportVO implements Serializable {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "统计开始日期")
    private LocalDate startDate;

    @Schema(description = "统计结束日期")
    private LocalDate endDate;

    @Schema(description = "累计入园人数")
    private Long totalEnterCount;

    @Schema(description = "累计出园人数")
    private Long totalLeaveCount;

    @Schema(description = "日均入园人数")
    private BigDecimal avgEnterCount;

    @Schema(description = "单日入园峰值")
    private Integer peakEnterCount;

    @Schema(description = "峰值出现日期")
    private String peakDate;

    @Schema(description = "期间最高在园人数")
    private Integer maxCurrentCount;

    @Schema(description = "承载量利用率（日均入园 / 日承载量，百分比）")
    private BigDecimal capacityUtilization;

    @Schema(description = "预警次数（WARNING 级）")
    private Long warningCount;

    @Schema(description = "超载次数（DANGER 级）")
    private Long dangerCount;

    @Schema(description = "按日入园趋势")
    private List<TrendItemVO> enterTrend = new ArrayList<>();

    @Schema(description = "按日入园与出园对比")
    private List<TrendItemVO> leaveTrend = new ArrayList<>();

    @Schema(description = "预警级别分布")
    private List<StatItemVO> warningLevelDist = new ArrayList<>();
}
