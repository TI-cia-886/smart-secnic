package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 推荐游览路线
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "推荐游览路线")
public class AppRouteVO implements Serializable {

    @Schema(description = "路线名称")
    private String routeName;

    @Schema(description = "路线说明")
    private String description;

    @Schema(description = "预计总时长（分钟）")
    private Integer totalDuration;

    @Schema(description = "景点数量")
    private Integer spotCount;

    @Schema(description = "出行提示")
    private String tip;

    @Schema(description = "路线景点，按游览顺序排列")
    private List<AppRouteSpotVO> spots = new ArrayList<>();
}
