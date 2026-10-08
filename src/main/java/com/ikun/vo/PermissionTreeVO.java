package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点
 *
 * <p>对应原型「角色权限管理」页面的勾选树：一级是菜单，二级是按钮。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "权限树节点")
public class PermissionTreeVO implements Serializable {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "父级ID，0 为顶级")
    private Long parentId;

    @Schema(description = "权限名称")
    private String permName;

    @Schema(description = "权限标识")
    private String permCode;

    @Schema(description = "类型：1菜单 2按钮")
    private Integer permType;

    @Schema(description = "前端路由地址")
    private String path;

    @Schema(description = "菜单图标")
    private String icon;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "当前角色是否已勾选")
    private Boolean checked;

    @Schema(description = "子节点")
    private List<PermissionTreeVO> children = new ArrayList<>();

    /** 是否为叶子节点，前端据此决定父节点是否半选 */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }
}
