package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @NotNull(message = "请填写购票数量")
    @Min(value = 1, message = "购票数量至少为 1")
    @Schema(description = "购票数量", example = "2")
    private Integer quantity;

    @NotBlank(message = "请填写取票人姓名")
    @Schema(description = "取票人姓名")
    private String contactName;

    @NotBlank(message = "请填写联系手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "联系手机号")
    private String contactPhone;

    @Schema(description = "游客备注")
    private String remark;
}
