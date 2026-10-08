package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投诉工单列表视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单信息")
public class ComplaintVO implements Serializable {

    @Schema(description = "工单ID")
    private Long id;

    @Schema(description = "工单编号")
    private String ticketNo;

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "游客ID")
    private Long touristId;

    @Schema(description = "游客姓名")
    private String touristName;

    @Schema(description = "联系电话（已脱敏）")
    private String phone;

    @Schema(description = "关联订单号")
    private String orderNo;

    @Schema(description = "工单标题")
    private String title;

    @Schema(description = "问题描述")
    private String content;

    @Schema(description = "类型码：COMPLAINT/SUGGESTION/CONSULT")
    private String type;

    @Schema(description = "类型中文")
    private String typeText;

    @Schema(description = "优先级码：NORMAL/URGENT")
    private String priority;

    @Schema(description = "优先级中文")
    private String priorityText;

    @Schema(description = "状态码：PENDING/PROCESSING/CLOSED")
    private String status;

    @Schema(description = "状态中文")
    private String statusText;

    @Schema(description = "AI 情感倾向：POSITIVE/NEUTRAL/NEGATIVE")
    private String sentiment;

    @Schema(description = "情感倾向中文")
    private String sentimentText;

    @Schema(description = "情感得分，-1.00 ~ 1.00")
    private BigDecimal sentimentScore;

    @Schema(description = "情感分析时间")
    private LocalDateTime sentimentTime;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @Schema(description = "处理人姓名")
    private String handlerName;

    @Schema(description = "处理完成时间")
    private LocalDateTime handleTime;

    @Schema(description = "提交时间")
    private LocalDateTime createTime;

    @Schema(description = "回复条数")
    private Integer replyCount;
}
