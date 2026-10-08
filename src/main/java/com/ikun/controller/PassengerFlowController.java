package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.FlowWarningHandleDTO;
import com.ikun.entity.CheckinRecord;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.PassengerFlow;
import com.ikun.service.PassengerFlowService;
import com.ikun.vo.FlowOverviewVO;
import com.ikun.vo.FlowReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客流统计接口（对应原型：客流统计页面）
 *
 * @author smart-scenic
 */
@Tag(name = "10-客流统计", description = "实时客流概览、检票记录、超载预警与客流报表")
@RestController
@RequestMapping("/flow")
@RequiredArgsConstructor
public class PassengerFlowController {

    private final PassengerFlowService passengerFlowService;

    @Operation(summary = "实时客流概览", description = "含当前在园、今日进出、饱和度与各景点实时明细")
    @RequirePerm("flow:list")
    @GetMapping("/overview")
    public Result<FlowOverviewVO> overview(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(passengerFlowService.overview(scenicId, date));
    }

    @Operation(summary = "分页查询客流时段数据")
    @RequirePerm("flow:list")
    @GetMapping("/page")
    public Result<PageResult<PassengerFlow>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(passengerFlowService.pageQuery(pageNum, pageSize, scenicId, startDate, endDate));
    }

    @Operation(summary = "分页查询检票核验记录")
    @RequirePerm("flow:checkin:list")
    @GetMapping("/checkin/page")
    public Result<PageResult<CheckinRecord>> checkinPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verifyType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(passengerFlowService.pageCheckin(pageNum, pageSize, scenicId, status,
                verifyType, orderNo, startTime, endTime));
    }

    @Operation(summary = "分页查询超载预警记录")
    @RequirePerm("flow:list")
    @GetMapping("/warning/page")
    public Result<PageResult<FlowWarning>> warningPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String warningLevel) {
        return Result.success(passengerFlowService.pageWarning(pageNum, pageSize, scenicId, status, warningLevel));
    }

    @Operation(summary = "处理客流预警")
    @RequirePerm("flow:warning:handle")
    @OperLog(title = "客流统计", businessType = "UPDATE")
    @PutMapping("/warning/handle")
    public Result<Void> handleWarning(@Valid @RequestBody FlowWarningHandleDTO dto) {
        passengerFlowService.handleWarning(dto);
        return Result.success("处置完成", null);
    }

    @Operation(summary = "客流数据报表", description = "含日均、峰值、承载量利用率与预警次数，默认统计近 30 天")
    @RequirePerm("flow:report")
    @GetMapping("/report")
    public Result<FlowReportVO> report(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(passengerFlowService.report(scenicId, startDate, endDate));
    }
}
