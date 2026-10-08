package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.TicketType;
import com.ikun.service.TicketTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 门票类型管理接口（对应原型：基础数据 → 门票类型管理）
 *
 * @author smart-scenic
 */
@Tag(name = "05-门票类型管理", description = "基础数据 - 票种的增删改查与上下架")
@RestController
@RequestMapping("/scenic/ticket")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @Operation(summary = "分页查询票种列表")
    @RequirePerm("scenic:ticket:list")
    @GetMapping("/page")
    public Result<PageResult<TicketType>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Long scenicId,
                                               @RequestParam(required = false) String ticketType,
                                               @RequestParam(required = false) Integer status) {
        return Result.success(ticketTypeService.pageQuery(pageNum, pageSize, keyword, scenicId, ticketType, status));
    }

    @Operation(summary = "查询票种详情")
    @RequirePerm("scenic:ticket:list")
    @GetMapping("/{id}")
    public Result<TicketType> detail(@PathVariable Long id) {
        return Result.success(ticketTypeService.getDetail(id));
    }

    @Operation(summary = "票种下拉选项", description = "status 不传表示全部，传 1 只返回上架票种")
    @RequirePerm("scenic:ticket:list")
    @GetMapping("/options")
    public Result<List<TicketType>> options(@RequestParam(required = false) Long scenicId,
                                            @RequestParam(required = false) Integer status) {
        return Result.success(ticketTypeService.listOptions(scenicId, status));
    }

    @Operation(summary = "新增票种")
    @RequirePerm("scenic:ticket:list")
    @OperLog(title = "门票类型管理", businessType = "INSERT")
    @PostMapping
    public Result<Void> save(@RequestBody TicketType ticketType) {
        ticketTypeService.saveTicketType(ticketType);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑票种")
    @RequirePerm("scenic:ticket:list")
    @OperLog(title = "门票类型管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@RequestBody TicketType ticketType) {
        ticketTypeService.updateTicketType(ticketType);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "上架/下架票种")
    @RequirePerm("scenic:ticket:list")
    @OperLog(title = "门票类型管理", businessType = "UPDATE")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        ticketTypeService.changeStatus(id, status);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "删除票种")
    @RequirePerm("scenic:ticket:list")
    @OperLog(title = "门票类型管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ticketTypeService.removeTicketType(id);
        return Result.success("删除成功", null);
    }
}
