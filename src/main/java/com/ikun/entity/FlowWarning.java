package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 超载预警记录表
 *
 * @author smart-scenic
 */
@Data
@TableName("flow_warning")
public class FlowWarning implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 景点ID（景区级预警为空） */
    private Long spotId;

    /** 景点名称 */
    private String spotName;

    /** 当前人数 */
    private Integer currentCount;

    /** 承载量 */
    private Integer capacity;

    /** 级别：WARNING预警 DANGER超载 */
    private String warningLevel;

    /** 状态：UNHANDLED未处理 HANDLED已处理 */
    private String status;

    /** 处置说明 */
    private String handleRemark;

    /** AI生成的客流疏导预案全文 */
    private String aiPlan;

    /** AI预案生成时间 */
    private LocalDateTime aiPlanTime;

    /** 处理人ID */
    private Long handlerId;

    /** 处理时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
