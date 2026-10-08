package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 门票销售报表视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "门票销售报表")
public class TicketReportVO implements Serializable {

    @Schema(description = "景区ID")
    private Long scenicId;

    @Schema(description = "景区名称")
    private String scenicName;

    @Schema(description = "统计开始日期")
    private LocalDate startDate;

    @Schema(description = "统计结束日期")
    private LocalDate endDate;

    @Schema(description = "销售总张数（不含已退款）")
    private Integer totalQuantity;

    @Schema(description = "销售总金额（不含已退款）")
    private BigDecimal totalAmount;

    @Schema(description = "已核销总张数")
    private Integer verifiedQuantity;

    @Schema(description = "综合核销率（百分比）")
    private BigDecimal verifiedRate;

    @Schema(description = "退款总张数")
    private Integer refundedQuantity;

    @Schema(description = "退款总金额")
    private BigDecimal refundedAmount;

    @Schema(description = "按票种明细")
    private List<TicketSaleItemVO> items = new ArrayList<>();

    @Schema(description = "按日销售张数趋势")
    private List<TrendItemVO> quantityTrend = new ArrayList<>();

    @Schema(description = "按日销售金额趋势")
    private List<TrendItemVO> amountTrend = new ArrayList<>();
}
