package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 新用户注册参数
 *
 * <p>注册时不接收角色参数，默认角色由服务端配置指定，避免越权注册高权限账号。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "新用户注册参数")
public class RegisterDTO {

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "账号需为 4-20 位字母、数字或下划线")
    @Schema(description = "登录账号", example = "zhangsan")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名", example = "张三")
    private String realName;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号（同时也是初始密码的来源）", example = "13800138000")
    private String phone;

    @Schema(description = "所属景区ID，为空表示全部景区")
    private Long scenicId;
}
