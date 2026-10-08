package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 推荐路线中的单个景点
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "路线景点")
public class AppRouteSpotVO implements Serializable {

    @Schema(description = "顺序号，从 1 开始")
    private Integer order;

    @Schema(description = "景点ID")
    private Long spotId;

    @Schema(description = "景点名称")
    private String spotName;

    @Schema(description = "景点类型：NATURAL/CULTURAL/PLAY")
    private String spotType;

    @Schema(description = "建议游玩时长（分钟）")
    private Integer suggestDuration;

    @Schema(description = "景点介绍")
    private String description;

    @Schema(description = "景点图片")
    private String coverImg;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "当前在园人数，用于提示是否拥挤")
    private Integer currentCount;

    @Schema(description = "状态：OPEN/MAINTENANCE/CLOSED")
    private String status;
}
