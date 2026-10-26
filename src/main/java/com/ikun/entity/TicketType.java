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

    /**
     * 分时时段列表，逗号分隔，如 {@code 08:00-10:00,10:00-12:00}。
     *
     * <p>为空表示「全天通用票」，库存仍按天归集；
     * 一旦配置，该票种下单必须选择时段，库存按 (日期 + 时段) 双维度扣减。</p>
     */
    private String timeSlots;

    /** 票种说明 */
    private String description;

    /** 状态：1上架 0下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：1已删除 0未删除（票种改为逻辑删除，避免历史订单引用的票种被物理抹掉） */
    @TableLogic
    private Integer deleted;
}
