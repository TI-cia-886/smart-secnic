package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录返回结果
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "登录返回结果")
public class LoginVO implements Serializable {

    @Schema(description = "访问令牌")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "所属景区ID")
    private Long scenicId;

    @Schema(description = "所属景区名称")
    private String scenicName;
}
