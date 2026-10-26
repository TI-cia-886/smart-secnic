package com.ikun.controller;

import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.ScenicArea;
import com.ikun.service.ScenicAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景区管理接口（对应原型：景区管理页面）
 *
 * @author smart-scenic
 */
@Tag(name = "03-景区管理", description = "景区信息的增删改查与下拉选项")
@RestController
@RequestMapping("/scenic/area")
@RequiredArgsConstructor
public class ScenicAreaController {

    private final ScenicAreaService scenicAreaService;

    @Operation(summary = "分页查询景区")
    @RequirePerm("scenic:area:list")
    @GetMapping("/page")
    public Result<PageResult<ScenicArea>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(required = false) String status) {
        return Result.success(scenicAreaService.pageQuery(pageNum, pageSize, keyword, level, status));
    }

    @Operation(summary = "景区下拉选项")
    // 说明：下拉选项是各业务页筛选条的基础数据，仅要求登录（JwtInterceptor 统一校验），
    // 不再要求 scenic:area:list（该权限仅用于「景区总管理」页面，超级管理员专属）。
    // 景区管理员在 Service 层被限定只能拿到本景区，避免越权看到全部景区列表。
    @GetMapping("/options")
    public Result<List<ScenicArea>> options() {
        return Result.success(scenicAreaService.listOptions());
    }

    @Operation(summary = "查询景区详情")
    @RequirePerm("scenic:area:list")
    @GetMapping("/{id}")
    public Result<ScenicArea> detail(@PathVariable Long id) {
        return Result.success(scenicAreaService.getById(id));
    }

    @Operation(summary = "新增景区")
    @RequirePerm("scenic:area:add")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody ScenicArea scenicArea) {
        scenicAreaService.saveScenicArea(scenicArea);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑景区")
    @RequirePerm("scenic:area:edit")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ScenicArea scenicArea) {
        scenicAreaService.updateScenicArea(scenicArea);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除景区")
    @RequirePerm("scenic:area:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scenicAreaService.removeScenicArea(id);
        return Result.success("删除成功", null);
    }
}
