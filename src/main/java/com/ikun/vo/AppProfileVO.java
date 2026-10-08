package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 小程序个人中心数据
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "个人中心数据")
public class AppProfileVO implements Serializable {

    @Schema(description = "游客基本信息")
    private TouristVO tourist;

    @Schema(description = "订单总数")
    private Long totalOrderCount;

    @Schema(description = "待支付订单数")
    private Long pendingPayCount;

    @Schema(description = "待使用（已支付未核销）订单数")
    private Long paidCount;

    @Schema(description = "已完成订单数")
    private Long verifiedCount;

    @Schema(description = "退款订单数（退款中 + 已退款）")
    private Long refundCount;

    @Schema(description = "工单总数")
    private Long complaintCount;

    @Schema(description = "处理中的工单数")
    private Long processingComplaintCount;
}
