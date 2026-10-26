package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 小程序下单参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "小程序下单参数")
public class AppOrderCreateDTO {

    @NotNull(message = "请选择票种")
    @Schema(description = "票种ID")
    private Long ticketTypeId;

    @NotNull(message = "请选择游玩日期")
    @Schema(description = "游玩日期")
    private LocalDate playDate;

    @Schema(description = "预约时段，如 08:00-10:00；票种开启分时预约时必填，全天票不传")
    private String timeSlot;

    @NotNull(message = "请填写购票数量")
    @Min(value = 1, message = "购票数量至少为 1")
    @Max(value = 20, message = "单次最多购买 20 张")
    @Schema(description = "购票数量", example = "2")
    private Integer quantity;

    @NotBlank(message = "请填写取票人姓名")
    @Size(max = 30, message = "取票人姓名不能超过 30 个字符")
    @Schema(description = "取票人姓名")
    private String contactName;

    @NotBlank(message = "请填写联系手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系手机号")
    private String contactPhone;

    @Size(max = 200, message = "备注不能超过 200 个字符")
    @Schema(description = "游客备注")
    private String remark;
}
