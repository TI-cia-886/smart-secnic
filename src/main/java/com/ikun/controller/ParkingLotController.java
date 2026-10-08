package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.ParkingLot;
import com.ikun.service.ParkingLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 停车场点位管理接口（对应原型：基础数据 → 停车场点位管理）
 *
 * @author smart-scenic
 */
@Tag(name = "07-停车场点位管理", description = "基础数据 - 停车场的增删改查与车位更新")
@RestController
@RequestMapping("/scenic/parking")
@RequiredArgsConstructor
public class ParkingLotController {

    private final ParkingLotService parkingLotService;

    @Operation(summary = "分页查询停车场列表")
    @RequirePerm("scenic:parking:list")
    @GetMapping("/page")
    public Result<PageResult<ParkingLot>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long scenicId,
                                               @RequestParam(required = false) String status) {
        return Result.success(parkingLotService.pageQuery(pageNum, pageSize, keyword, scenicId, status));
    }

    @Operation(summary = "查询停车场详情")
    @RequirePerm("scenic:parking:list")
    @GetMapping("/{id}")
    public Result<ParkingLot> detail(@PathVariable Long id) {
        return Result.success(parkingLotService.getDetail(id));
    }

    @Operation(summary = "按景区查询停车场列表")
    @RequirePerm("scenic:parking:list")
    @GetMapping("/list")
    public Result<List<ParkingLot>> list(@RequestParam(required = false) Long scenicId) {
        return Result.success(parkingLotService.listByScenic(scenicId));
    }

    @Operation(summary = "新增停车场")
    @RequirePerm("scenic:parking:list")
    @OperLog(title = "停车场点位管理", businessType = "INSERT")
    @PostMapping
    public Result<Void> save(@RequestBody ParkingLot parkingLot) {
        parkingLotService.saveParkingLot(parkingLot);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑停车场")
    @RequirePerm("scenic:parking:list")
    @OperLog(title = "停车场点位管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@RequestBody ParkingLot parkingLot) {
        parkingLotService.updateParkingLot(parkingLot);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "更新剩余车位", description = "拥堵状态由剩余比例自动推导")
    @RequirePerm("scenic:parking:list")
    @OperLog(title = "停车场点位管理", businessType = "UPDATE")
    @PutMapping("/{id}/free-space")
    public Result<Void> updateFreeSpace(@PathVariable Long id, @RequestParam Integer freeSpace) {
        parkingLotService.updateFreeSpace(id, freeSpace);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "删除停车场")
    @RequirePerm("scenic:parking:list")
    @OperLog(title = "停车场点位管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        parkingLotService.removeParkingLot(id);
        return Result.success("删除成功", null);
    }
}
