package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "当前登录用户信息")
public class UserInfoVO implements Serializable {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "所属景区ID")
    private Long scenicId;

    @Schema(description = "所属景区名称")
    private String scenicName;

    @Schema(description = "权限标识集合")
    private List<String> permissions;
}
