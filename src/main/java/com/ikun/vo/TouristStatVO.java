package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 游客注册统计视图对象（对应原型：游客信息管理 → 注册统计图表）
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客注册统计")
public class TouristStatVO implements Serializable {

    @Schema(description = "游客总数（统计范围内注册）")
    private Long totalCount;

    @Schema(description = "今日新增游客数")
    private Long todayNewCount;

    @Schema(description = "已实名游客数")
    private Long realNameCount;

    @Schema(description = "实名率（百分比，保留两位小数）")
    private BigDecimal realNameRate;

    @Schema(description = "黑名单人数（全量，不受时间范围限制）")
    private Long blacklistCount;

    @Schema(description = "注册趋势（按日）")
    private List<TrendItemVO> trend = new ArrayList<>();

    @Schema(description = "会员等级分布")
    private List<StatItemVO> memberLevelDist = new ArrayList<>();

    @Schema(description = "注册来源分布")
    private List<StatItemVO> sourceDist = new ArrayList<>();

    @Schema(description = "性别分布")
    private List<StatItemVO> genderDist = new ArrayList<>();
}
