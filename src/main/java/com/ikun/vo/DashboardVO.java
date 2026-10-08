package com.ikun.vo;

import com.ikun.entity.Announcement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 数据概览统计结果（对应原型：数据概览页面）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "数据概览统计结果")
public class DashboardVO implements Serializable {

    @Schema(description = "景区总数")
    private Long scenicCount;

    @Schema(description = "游客总数")
    private Long touristCount;

    @Schema(description = "今日新增游客数")
    private Long todayNewTouristCount;

    @Schema(description = "今日订单数")
    private Long todayOrderCount;

    @Schema(description = "今日营收（元）")
    private BigDecimal todayOrderAmount;

    @Schema(description = "待处理工单数")
    private Long pendingComplaintCount;

    @Schema(description = "未处理预警数")
    private Long unhandledWarningCount;

    @Schema(description = "今日入园人数")
    private Long todayEnterCount;

    @Schema(description = "当前在园人数")
    private Long currentOnlineCount;

    @Schema(description = "在园人数（前端承载压力计算用，与 currentOnlineCount 一致）")
    private Long onlineCount;

    @Schema(description = "日承载量（按当前景区或全部启用景区合计）")
    private Long capacity;

    @Schema(description = "天气（暂无数据源时为空）")
    private String weather;

    @Schema(description = "AI 响应成功率（0~1，小数）")
    private BigDecimal aiResponseRate;

    @Schema(description = "订单转化率（0~1，小数，近 7 日已支付订单 / 入园人次）")
    private BigDecimal orderConversion;

    @Schema(description = "营收日环比（百分比，如 12.34 表示 +12.34%）")
    private BigDecimal revenueDelta;

    @Schema(description = "黑名单占比（0~1，小数）")
    private BigDecimal blacklistRate;

    @Schema(description = "近 7 日入园人次趋势（按日期升序，最后一项为今天）")
    private List<Long> todayEnterTrend;

    @Schema(description = "近 7 日在园人数峰值趋势（按日期升序，最后一项为今天）")
    private List<Long> onlineTrend;

    @Schema(description = "近 7 日订单数趋势（按游玩日期升序）")
    private List<Long> orderTrend;

    @Schema(description = "近 7 日营收趋势（按游玩日期升序，单位：元）")
    private List<Long> revenueTrend;

    @Schema(description = "近 7 日新增工单趋势（按创建日期升序）")
    private List<Long> complaintTrend;

    @Schema(description = "今日 24 小时入园人次分布（下标即小时 0~23）")
    private List<Integer> hourlyDistribution;

    @Schema(description = "最新公告（最多 6 条，已发布）")
    private List<Announcement> announcementList;
}
