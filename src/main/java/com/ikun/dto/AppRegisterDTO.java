package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 游客小程序注册参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客小程序注册参数")
public class AppRegisterDTO {

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @NotBlank(message = "请输入密码")
    @Pattern(regexp = "^\\S{6,20}$", message = "密码需为 6-20 位且不含空格")
    @Schema(description = "密码")
    private String password;

    @Schema(description = "真实姓名，选填；填写后视为已实名")
    private String realName;

    @Schema(description = "证件号码，选填；填写后视为已实名")
    private String idCard;

    @Schema(description = "注册来源景区ID，小程序扫码进入时可带上")
    private Long scenicId;
}
