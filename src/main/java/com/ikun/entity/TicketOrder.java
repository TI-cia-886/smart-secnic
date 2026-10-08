package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门票订单表
 *
 * @author smart-scenic
 */
@Data
@TableName("ticket_order")
public class TicketOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 游客ID */
    private Long touristId;

    /** 景区ID */
    private Long scenicId;

    /** 票种ID */
    private Long ticketTypeId;

    /** 票种名称（冗余） */
    private String ticketName;

    /** 购票数量 */
    private Integer quantity;

    /** 单价（元） */
    private BigDecimal unitPrice;

    /** 订单总额（元） */
    private BigDecimal totalAmount;

    /** 游玩日期 */
    private LocalDate playDate;

    /** 状态：PENDING_PAY/PAID/VERIFIED/REFUNDING/REFUNDED/CANCELLED */
    private String status;

    /** 购票渠道：MINI_PROGRAM/OTA/WINDOW/AGENCY */
    private String channel;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 核销时间 */
    private LocalDateTime verifyTime;

    /** 退款时间 */
    private LocalDateTime refundTime;

    /** 支付截止时间，超时由定时任务自动取消 */
    private LocalDateTime expireTime;

    /** 退款原因（游客填写） */
    private String refundReason;

    /** 退票申请时间 */
    private LocalDateTime refundApplyTime;

    /** 退票审核人ID（sys_user.id） */
    private Long refundAuditBy;

    /** 退票审核时间 */
    private LocalDateTime refundAuditTime;

    /** 退票审核意见（驳回时必填） */
    private String refundAuditRemark;

    /** 电子票二维码内容（订单号+签名），前端据此渲染二维码 */
    private String qrCode;

    /** 取票人姓名 */
    private String contactName;

    /** 取票人手机号 */
    private String contactPhone;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
