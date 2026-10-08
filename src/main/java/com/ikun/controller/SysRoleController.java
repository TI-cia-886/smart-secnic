package com.ikun.controller;

import com.ikun.annotation.RequirePerm;
import com.ikun.annotation.OperLog;
import com.ikun.common.Result;
import com.ikun.dto.RolePermissionDTO;
import com.ikun.dto.SysRoleDTO;
import com.ikun.service.SysRoleService;
import com.ikun.vo.PermissionTreeVO;
import com.ikun.vo.SysRoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色权限管理接口（对应原型：角色权限管理页面）
 *
 * <p>权限标识说明：种子数据里本模块只定义了 {@code system:role:list}（菜单）
 * 与 {@code system:role:assign}（分配权限按钮），因此角色的增删改复用这两个标识，
 * 未额外新增权限项，避免出现「界面能进、点保存被拦」的不一致。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "03-角色权限管理", description = "角色的增删改查与权限分配")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    @Operation(summary = "查询角色列表")
    @RequirePerm("system:role:list")
    @GetMapping("/list")
    public Result<List<SysRoleVO>> list() {
        return Result.success(sysRoleService.listAll());
    }

    @Operation(summary = "查询角色详情")
    @RequirePerm("system:role:list")
    @GetMapping("/{id}")
    public Result<SysRoleVO> detail(@PathVariable Long id) {
        return Result.success(sysRoleService.getDetail(id));
    }

    @Operation(summary = "查询权限树", description = "传入 roleId 时返回该角色的勾选状态")
    @RequirePerm("system:role:list")
    @GetMapping("/permission-tree")
    public Result<List<PermissionTreeVO>> permissionTree(@RequestParam(required = false) Long roleId) {
        return Result.success(sysRoleService.permissionTree(roleId));
    }

    @Operation(summary = "新增角色")
    @RequirePerm("system:role:list")
    @OperLog(title = "角色权限管理", businessType = "INSERT")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody SysRoleDTO dto) {
        sysRoleService.saveRole(dto);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑角色")
    @RequirePerm("system:role:list")
    @OperLog(title = "角色权限管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysRoleDTO dto) {
        sysRoleService.updateRole(dto);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除角色")
    @RequirePerm("system:role:list")
    @OperLog(title = "角色权限管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.removeRole(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "为角色分配权限")
    @RequirePerm("system:role:assign")
    @OperLog(title = "角色权限管理", businessType = "GRANT")
    @PutMapping("/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @RequestBody RolePermissionDTO dto) {
        // 以路径参数为准，避免请求体与路径不一致时产生歧义
        dto.setRoleId(id);
        sysRoleService.assignPermissions(dto);
        return Result.success("权限分配成功", null);
    }
}
