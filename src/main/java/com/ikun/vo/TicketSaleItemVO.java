package com.ikun.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门票销售明细项（按票种维度）
 *
 * <p>同时作为报表接口的返回项与 Excel 导出的行对象。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "门票销售明细")
public class TicketSaleItemVO implements Serializable {

    @Schema(description = "票种ID")
    private Long ticketTypeId;

    @ExcelProperty("景区")
    @ColumnWidth(16)
    @Schema(description = "景区名称")
    private String scenicName;

    @ExcelProperty("票种")
    @ColumnWidth(18)
    @Schema(description = "票种名称")
    private String ticketName;

    @ExcelProperty("销售张数")
    @ColumnWidth(12)
    @Schema(description = "有效销售张数（不含已退款）")
    private Integer quantity;

    @ExcelProperty("销售金额(元)")
    @ColumnWidth(16)
    @Schema(description = "有效销售金额（不含已退款）")
    private BigDecimal amount;

    @ExcelProperty("已核销张数")
    @ColumnWidth(14)
    @Schema(description = "已核销张数")
    private Integer verifiedQuantity;

    @ExcelProperty("核销率")
    @ColumnWidth(12)
    @Schema(description = "核销率（百分比）")
    private BigDecimal verifiedRate;

    @ExcelProperty("退款张数")
    @ColumnWidth(12)
    @Schema(description = "已退款张数")
    private Integer refundedQuantity;

    @ExcelProperty("退款金额(元)")
    @ColumnWidth(16)
    @Schema(description = "已退款金额")
    private BigDecimal refundedAmount;
}
