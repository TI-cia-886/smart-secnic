package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单统计视图对象（对应原型：订单预约管理 → 数据统计）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "订单统计")
public class OrderStatVO implements Serializable {

    @Schema(description = "订单总数（统计范围内下单）")
    private Long totalCount;

    @Schema(description = "待支付订单数")
    private Long pendingPayCount;

    @Schema(description = "已支付待核销订单数")
    private Long paidCount;

    @Schema(description = "已核销订单数")
    private Long verifiedCount;

    @Schema(description = "退款处理中订单数")
    private Long refundingCount;

    @Schema(description = "已退款订单数")
    private Long refundedCount;

    @Schema(description = "已取消订单数")
    private Long cancelledCount;

    @Schema(description = "累计营收（已支付与已核销订单金额，不含已退款）")
    private BigDecimal totalRevenue;

    @Schema(description = "今日订单数")
    private Long todayOrderCount;

    @Schema(description = "今日营收")
    private BigDecimal todayRevenue;

    @Schema(description = "退票率（百分比，保留两位小数）")
    private BigDecimal refundRate;

    @Schema(description = "订单量趋势（按日）")
    private List<TrendItemVO> orderTrend = new ArrayList<>();

    @Schema(description = "营收趋势（按日）")
    private List<TrendItemVO> revenueTrend = new ArrayList<>();

    @Schema(description = "订单状态分布")
    private List<StatItemVO> statusDist = new ArrayList<>();

    @Schema(description = "购票渠道分布")
    private List<StatItemVO> channelDist = new ArrayList<>();
}
