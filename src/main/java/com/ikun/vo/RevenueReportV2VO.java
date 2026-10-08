package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 营收报表视图对象
 *
 * <p>命名为 V2 是为了与既有的 {@link DashboardVO} 中的营收字段区分开，
 * 本对象表达的是「带时间维度的营收分析」。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "营收报表")
public class RevenueReportV2VO implements Serializable {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "统计开始日期")
    private LocalDate startDate;

    @Schema(description = "统计结束日期")
    private LocalDate endDate;

    @Schema(description = "订单总数（统计范围内下单）")
    private Long totalOrders;

    @Schema(description = "毛营收（已支付 + 已核销订单金额）")
    private BigDecimal grossRevenue;

    @Schema(description = "退款金额（已退款订单金额）")
    private BigDecimal refundAmount;

    @Schema(description = "净营收（毛营收 - 退款金额）")
    private BigDecimal netRevenue;

    @Schema(description = "客单价（毛营收 / 有效订单数）")
    private BigDecimal avgOrderAmount;

    @Schema(description = "按日营收趋势")
    private List<TrendItemVO> revenueTrend = new ArrayList<>();

    @Schema(description = "按日订单量趋势")
    private List<TrendItemVO> orderTrend = new ArrayList<>();

    @Schema(description = "渠道营收分布")
    private List<StatItemVO> channelDist = new ArrayList<>();

    @Schema(description = "景区营收分布（多景区对比）")
    private List<StatItemVO> scenicDist = new ArrayList<>();
}
