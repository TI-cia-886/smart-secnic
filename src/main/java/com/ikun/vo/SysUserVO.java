package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台账号列表 VO
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "后台账号列表 VO")
public class SysUserVO implements Serializable {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "角色ID")
    private Long roleId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "所属景区ID")
    private Long scenicId;

    @Schema(description = "所属景区名称（为空表示全部景区）")
    private String scenicName;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "最近登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
