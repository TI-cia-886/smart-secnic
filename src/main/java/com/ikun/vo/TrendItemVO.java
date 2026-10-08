package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 通用趋势图数据点
 *
 * <p>{@code value} 用 BigDecimal 而不是 Long，因为客流量、营收这类指标
 * 既有计数也有金额，统一成一种类型前端不用分情况处理。</p>
 *
 * @author smart-scenic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "趋势图数据点")
public class TrendItemVO implements Serializable {

    @Schema(description = "日期，格式 yyyy-MM-dd")
    private String date;

    @Schema(description = "指标数值")
    private BigDecimal value;

    public TrendItemVO(String date, Long value) {
        this.date = date;
        this.value = value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
    }
}
