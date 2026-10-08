package com.ikun.vo;

import com.ikun.entity.Announcement;
import com.ikun.entity.ParkingLot;
import com.ikun.entity.ScenicSpot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 小程序首页聚合数据
 *
 * <p>首页需要景区信息、公告、客流提示、热门景点、停车场五块数据。
 * 合并成一个接口返回，避免小程序首屏发 5 个请求——移动端每次请求
 * 都要重新握手，串行发请求会让首屏白屏时间成倍增加。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "小程序首页数据")
public class AppHomeVO implements Serializable {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "景区等级")
    private String level;

    @Schema(description = "详细地址")
    private String address;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "封面图")
    private String coverImg;

    @Schema(description = "景区简介")
    private String description;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "日承载量")
    private Integer dailyCapacity;

    @Schema(description = "当前在园人数")
    private Integer currentCount;

    @Schema(description = "承载力饱和度（百分比）")
    private BigDecimal saturationRate;

    @Schema(description = "客流提示文案，如「当前舒适」「较为拥挤」")
    private String flowTip;

    @Schema(description = "客流级别：NORMAL/WARNING/DANGER")
    private String flowLevel;

    @Schema(description = "待使用订单数量，未登录时为 0")
    private Long pendingOrderCount;

    @Schema(description = "公告列表")
    private List<Announcement> announcements = new ArrayList<>();

    @Schema(description = "热门景点（按当前人数排序）")
    private List<ScenicSpot> hotSpots = new ArrayList<>();

    @Schema(description = "停车场列表")
    private List<ParkingLot> parkingLots = new ArrayList<>();
}
