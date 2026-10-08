package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限表（树形菜单 + 按钮）
 *
 * @author smart-scenic
 */
@Data
@TableName("sys_permission")
public class SysPermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父级ID，0 为顶级 */
    private Long parentId;

    /** 权限名称 */
    private String permName;

    /** 权限标识，如 scenic:area:list */
    private String permCode;

    /** 类型：1菜单 2按钮 */
    private Integer permType;

    /** 前端路由地址 */
    private String path;

    /** 菜单图标 */
    private String icon;

    /** 排序值 */
    private Integer sort;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
