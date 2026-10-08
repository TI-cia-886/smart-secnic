package com.ikun.controller;

import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.SysUserDTO;
import com.ikun.service.SysUserService;
import com.ikun.vo.SysUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 账号管理接口（对应原型：账号管理页面）
 *
 * @author smart-scenic
 */
@Tag(name = "02-账号管理", description = "后台账号的增删改查、启用禁用、密码重置")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    @Operation(summary = "分页查询账号列表")
    @RequirePerm("system:user:list")
    @GetMapping("/page")
    public Result<PageResult<SysUserVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long roleId,
                                              @RequestParam(required = false) Long scenicId,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(sysUserService.pageQuery(pageNum, pageSize, keyword, roleId, scenicId, status));
    }

    @Operation(summary = "查询账号详情")
    @RequirePerm("system:user:list")
    @GetMapping("/{id}")
    public Result<SysUserVO> detail(@PathVariable Long id) {
        return Result.success(sysUserService.getDetail(id));
    }

    @Operation(summary = "新增账号")
    @RequirePerm("system:user:add")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody SysUserDTO dto) {
        sysUserService.saveUser(dto);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑账号")
    @RequirePerm("system:user:edit")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysUserDTO dto) {
        sysUserService.updateUser(dto);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除账号")
    @RequirePerm("system:user:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeUser(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "启用/禁用账号")
    @RequirePerm("system:user:edit")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        sysUserService.changeStatus(id, status);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "重置密码为手机号后六位")
    @RequirePerm("system:user:reset")
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        sysUserService.resetPassword(id);
        return Result.success("密码已重置为手机号后六位", null);
    }
}
