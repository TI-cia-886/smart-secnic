package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景点表
 *
 * @author smart-scenic
 */
@Data
@TableName("scenic_spot")
public class ScenicSpot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景点编号 */
    private String spotCode;

    /** 所属景区ID */
    private Long scenicId;

    /** 景点名称 */
    private String spotName;

    /** 类型：NATURAL自然景观 CULTURAL人文景观 PLAY游乐项目 */
    private String spotType;

    /** 建议游玩时长（分钟） */
    private Integer suggestDuration;

    /** 瞬时承载量（人） */
    private Integer instantCapacity;

    /** 当前在园人数 */
    private Integer currentCount;

    /** 单独门票价（元） */
    private BigDecimal price;

    /** 状态：OPEN开放 MAINTENANCE维护中 CLOSED关闭 */
    private String status;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 景点图片 */
    private String coverImg;

    /** 景点介绍 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
