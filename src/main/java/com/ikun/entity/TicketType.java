package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门票类型表
 *
 * @author smart-scenic
 */
@Data
@TableName("ticket_type")
public class TicketType implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属景区ID */
    private Long scenicId;

    /** 票种名称 */
    private String ticketName;

    /** 票种编码：ADULT/CHILD/STUDENT/SENIOR/FAMILY/PACKAGE/GROUP */
    private String ticketType;

    /** 售价（元） */
    private BigDecimal price;

    /** 原价（元） */
    private BigDecimal originalPrice;

    /** 每日库存 */
    private Integer stock;

    /** 票种说明 */
    private String description;

    /** 状态：1上架 0下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
