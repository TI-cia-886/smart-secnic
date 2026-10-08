package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车场表
 *
 * @author smart-scenic
 */
@Data
@TableName("parking_lot")
public class ParkingLot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属景区ID */
    private Long scenicId;

    /** 停车场名称 */
    private String lotName;

    /** 总车位数 */
    private Integer totalSpace;

    /** 剩余车位 */
    private Integer freeSpace;

    /** 状态：FREE空闲 BUSY紧张 FULL已满 */
    private String status;

    /** 收费标准 */
    private String feeRule;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
