package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 景点实时客流视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "景点实时客流")
public class SpotFlowVO implements Serializable {

    @Schema(description = "景点ID")
    private Long spotId;

    @Schema(description = "景点名称")
    private String spotName;

    @Schema(description = "景点类型：NATURAL/CULTURAL/PLAY")
    private String spotType;

    @Schema(description = "当前在园人数")
    private Integer currentCount;

    @Schema(description = "瞬时承载量")
    private Integer instantCapacity;

    @Schema(description = "饱和度（百分比），承载量为空时为 0")
    private BigDecimal saturationRate;

    @Schema(description = "预警级别：NORMAL/WARNING/DANGER")
    private String warningLevel;

    @Schema(description = "景点状态：OPEN/MAINTENANCE/CLOSED")
    private String status;
}
