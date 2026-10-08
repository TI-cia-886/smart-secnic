package com.ikun.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 后台账号新增/编辑参数
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "后台账号新增/编辑参数")
public class SysUserDTO {

    @Schema(description = "主键，新增时为空")
    private Long id;

    @NotBlank(message = "登录账号不能为空")
    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "登录密码，新增时必填；编辑时留空表示不修改")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像")
    private String avatar;

    @NotNull(message = "请选择角色")
    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "所属景区ID，为空表示全部景区")
    private Long scenicId;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;
}
