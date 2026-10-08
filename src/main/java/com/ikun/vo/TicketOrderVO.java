package com.ikun.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单视图对象
 *
 * <p>导出用的中文字段通过 {@code @ExcelProperty} 直接标注在本对象上，
 * 订单列表与导出共用一份数据结构，避免再维护一个几乎相同的导出 DTO。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "订单信息")
public class TicketOrderVO implements Serializable {

    @Schema(description = "订单ID")
    private Long id;

    @ExcelProperty("订单号")
    @ColumnWidth(24)
    @Schema(description = "订单号")
    private String orderNo;

    @ExcelProperty("游客姓名")
    @ColumnWidth(12)
    @Schema(description = "游客姓名")
    private String touristName;

    @Schema(description = "游客ID")
    private Long touristId;

    @ExcelProperty("景区")
    @ColumnWidth(16)
    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "景区ID")
    private Long scenicId;

    @ExcelProperty("票种")
    @ColumnWidth(14)
    @Schema(description = "票种名称")
    private String ticketName;

    @Schema(description = "票种ID")
    private Long ticketTypeId;

    @ExcelProperty("数量")
    @ColumnWidth(8)
    @Schema(description = "购票数量")
    private Integer quantity;

    @ExcelProperty("单价(元)")
    @ColumnWidth(12)
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    @ExcelProperty("订单金额(元)")
    @ColumnWidth(14)
    @Schema(description = "订单总额")
    private BigDecimal totalAmount;

    @ExcelProperty("游玩日期")
    @ColumnWidth(14)
    @Schema(description = "游玩日期")
    private LocalDate playDate;

    @ExcelProperty("订单状态")
    @ColumnWidth(12)
    @Schema(description = "订单状态中文描述")
    private String statusText;

    @Schema(description = "订单状态码：PENDING_PAY/PAID/VERIFIED/REFUNDING/REFUNDED/CANCELLED")
    private String status;

    @ExcelProperty("购票渠道")
    @ColumnWidth(12)
    @Schema(description = "购票渠道中文描述")
    private String channelText;

    @Schema(description = "购票渠道码：MINI_PROGRAM/OTA/WINDOW/AGENCY")
    private String channel;

    @ExcelProperty("下单时间")
    @ColumnWidth(22)
    @Schema(description = "下单时间")
    private LocalDateTime createTime;

    @Schema(description = "支付时间")
    private LocalDateTime payTime;

    @ExcelProperty("核销时间")
    @ColumnWidth(22)
    @Schema(description = "核销时间")
    private LocalDateTime verifyTime;

    @Schema(description = "支付截止时间")
    private LocalDateTime expireTime;

    @Schema(description = "退款申请时间")
    private LocalDateTime refundApplyTime;

    @Schema(description = "退款原因")
    private String refundReason;

    @Schema(description = "退票审核意见")
    private String refundAuditRemark;

    @Schema(description = "退票审核人")
    private String refundAuditByName;

    @Schema(description = "电子票二维码内容")
    private String qrCode;

    @Schema(description = "取票人姓名")
    private String contactName;

    @Schema(description = "取票人手机号")
    private String contactPhone;

    @Schema(description = "备注")
    private String remark;
}
