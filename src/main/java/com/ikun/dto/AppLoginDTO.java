package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 游客小程序登录参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客小程序登录参数")
public class AppLoginDTO {

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "请输入密码")
    @Schema(description = "密码")
    private String password;
}
