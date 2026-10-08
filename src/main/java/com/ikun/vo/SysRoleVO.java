package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色列表/详情视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "角色信息")
public class SysRoleVO implements Serializable {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "状态：1启用 0禁用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "关联账号数量，用于列表展示与删除前提示")
    private Long userCount;

    @Schema(description = "已授权的权限数量")
    private Long permissionCount;

    @Schema(description = "是否内置角色：内置角色不允许删除，避免系统失去唯一超管")
    private Boolean builtin;

    @Schema(description = "已授权的权限ID集合，仅详情接口返回")
    private List<Long> permissionIds;
}
