package com.ikun.service;

import com.ikun.dto.RolePermissionDTO;
import com.ikun.dto.SysRoleDTO;
import com.ikun.vo.PermissionTreeVO;
import com.ikun.vo.SysRoleVO;

import java.util.List;

/**
 * 角色权限服务
 *
 * @author smart-scenic
 */
public interface SysRoleService {

    /** 查询全部角色（含账号数量统计） */
    List<SysRoleVO> listAll();

    /** 查询角色详情，含已授权限ID */
    SysRoleVO getDetail(Long id);

    /** 新增角色 */
    void saveRole(SysRoleDTO dto);

    /** 编辑角色 */
    void updateRole(SysRoleDTO dto);

    /** 删除角色（有关联账号时拒绝） */
    void removeRole(Long id);

    /**
     * 查询权限树
     *
     * @param roleId 传入时标记每个节点对该角色是否已勾选；为空则全部未勾选
     */
    List<PermissionTreeVO> permissionTree(Long roleId);

    /** 为角色分配权限（全量覆盖） */
    void assignPermissions(RolePermissionDTO dto);
}
