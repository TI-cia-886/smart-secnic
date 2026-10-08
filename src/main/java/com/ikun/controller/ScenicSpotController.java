package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.ScenicSpot;
import com.ikun.service.ScenicSpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 景点管理接口（对应原型：基础数据 → 景点管理）
 *
 * <p>权限说明：种子数据中本模块仅定义菜单级 {@code scenic:spot:list}，
 * 未细分增删改按钮权限，故 CRUD 统一使用该标识。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "04-景点管理", description = "基础数据 - 景点的增删改查")
@RestController
@RequestMapping("/scenic/spot")
@RequiredArgsConstructor
public class ScenicSpotController {

    private final ScenicSpotService scenicSpotService;

    @Operation(summary = "分页查询景点列表")
    @RequirePerm("scenic:spot:list")
    @GetMapping("/page")
    public Result<PageResult<ScenicSpot>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long scenicId,
                                               @RequestParam(required = false) String spotType,
                                               @RequestParam(required = false) String status) {
        return Result.success(scenicSpotService.pageQuery(pageNum, pageSize, keyword, scenicId, spotType, status));
    }

    @Operation(summary = "查询景点详情")
    @RequirePerm("scenic:spot:list")
    @GetMapping("/{id}")
    public Result<ScenicSpot> detail(@PathVariable Long id) {
        return Result.success(scenicSpotService.getDetail(id));
    }

    @Operation(summary = "景点下拉选项")
    @RequirePerm("scenic:spot:list")
    @GetMapping("/options")
    public Result<List<ScenicSpot>> options(@RequestParam(required = false) Long scenicId) {
        return Result.success(scenicSpotService.listOptions(scenicId));
    }

    @Operation(summary = "新增景点")
    @RequirePerm("scenic:spot:list")
    @OperLog(title = "景点管理", businessType = "INSERT")
    @PostMapping
    public Result<Void> save(@RequestBody ScenicSpot spot) {
        scenicSpotService.saveSpot(spot);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑景点")
    @RequirePerm("scenic:spot:list")
    @OperLog(title = "景点管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@RequestBody ScenicSpot spot) {
        scenicSpotService.updateSpot(spot);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除景点")
    @RequirePerm("scenic:spot:list")
    @OperLog(title = "景点管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scenicSpotService.removeSpot(id);
        return Result.success("删除成功", null);
    }
}
