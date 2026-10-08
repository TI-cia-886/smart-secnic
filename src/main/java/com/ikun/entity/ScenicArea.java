package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 景区表
 *
 * @author smart-scenic
 */
@Data
@TableName("scenic_area")
public class ScenicArea implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区编码 */
    private String scenicCode;

    /** 景区名称 */
    private String scenicName;

    /** 景区等级：5A/4A/3A */
    private String level;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 详细地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 日承载量（人） */
    private Integer dailyCapacity;

    /** 负责人 */
    private String manager;

    /** 联系电话 */
    private String phone;

    /** 封面图 */
    private String coverImg;

    /** 景区简介 */
    private String description;

    /** 状态：ENABLE启用 DISABLE停用 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
