package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投诉工单表
 *
 * @author smart-scenic
 */
@Data
@TableName("complaint")
public class Complaint implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号 */
    private String ticketNo;

    /** 景区ID */
    private Long scenicId;

    /** 游客ID */
    private Long touristId;

    /** 游客姓名 */
    private String touristName;

    /** 联系电话 */
    private String phone;

    /** 关联订单号 */
    private String orderNo;

    /** 工单标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 类型：COMPLAINT投诉 SUGGESTION建议 CONSULT咨询 */
    private String type;

    /** 优先级：NORMAL普通 URGENT紧急 */
    private String priority;

    /** 状态：PENDING待处理 PROCESSING处理中 CLOSED已完结 */
    private String status;

    /** AI情感倾向：POSITIVE积极 NEUTRAL中性 NEGATIVE消极 */
    private String sentiment;

    /** AI情感得分，区间 -1.000 ~ 1.000 */
    private java.math.BigDecimal sentimentScore;

    /** 情感分析时间 */
    private LocalDateTime sentimentTime;

    /** 处理人ID */
    private Long handlerId;

    /** 处理完成时间 */
    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
