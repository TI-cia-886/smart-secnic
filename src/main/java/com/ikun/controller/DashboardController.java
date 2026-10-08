package com.ikun.controller;

import com.ikun.annotation.RequirePerm;
import com.ikun.common.Result;
import com.ikun.service.DashboardService;
import com.ikun.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据概览接口（对应原型：PC后台/管理端移动版 首页看板）
 *
 * @author smart-scenic
 */
@Tag(name = "04-数据概览", description = "首页统计数据卡片")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "首页统计数据")
    @RequirePerm("dashboard:view")
    @GetMapping("/stats")
    public Result<DashboardVO> stats(@RequestParam(required = false) Long scenicId) {
        return Result.success(dashboardService.stats(scenicId));
    }
}
