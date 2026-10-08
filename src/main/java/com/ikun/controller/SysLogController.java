package com.ikun.controller;

import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.SysLoginLog;
import com.ikun.entity.SysOperLog;
import com.ikun.service.SysLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 系统日志接口（登录日志 / 操作日志）
 *
 * <p>日志是只读数据，不提供删除接口——审计记录可被操作者抹掉就失去意义了。
 * 清理请走数据库定时任务。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "12-系统日志", description = "登录日志与操作日志查询")
@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService sysLogService;

    @Operation(summary = "分页查询登录日志")
    @RequirePerm("system:loginlog:list")
    @GetMapping("/login-log/page")
    public Result<PageResult<SysLoginLog>> loginLogPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String loginType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(sysLogService.pageLoginLog(pageNum, pageSize, username, status,
                loginType, startDate, endDate));
    }

    @Operation(summary = "分页查询操作日志")
    @RequirePerm("system:operlog:list")
    @GetMapping("/oper-log/page")
    public Result<PageResult<SysOperLog>> operLogPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(sysLogService.pageOperLog(pageNum, pageSize, title, businessType,
                operatorName, status, startDate, endDate));
    }

    @Operation(summary = "查询操作日志详情", description = "含请求参数与返回值")
    @RequirePerm("system:operlog:list")
    @GetMapping("/oper-log/{id}")
    public Result<SysOperLog> operLogDetail(@PathVariable Long id) {
        return Result.success(sysLogService.getOperLog(id));
    }
}
