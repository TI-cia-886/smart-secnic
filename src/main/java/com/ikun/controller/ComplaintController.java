package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.ComplaintAssignDTO;
import com.ikun.dto.ComplaintReplyDTO;
import com.ikun.dto.ComplaintSummaryDTO;
import com.ikun.service.ComplaintService;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintSummaryVO;
import com.ikun.vo.ComplaintVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 投诉工单管理接口（对应原型：投诉工单管理页面）
 *
 * @author smart-scenic
 */
@Tag(name = "11-投诉工单管理", description = "工单查询、回复、转派、情感分析与归纳汇总")
@RestController
@RequestMapping("/complaint")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "分页查询工单列表")
    @RequirePerm("complaint:list")
    @GetMapping("/page")
    public Result<PageResult<ComplaintVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(complaintService.pageQuery(pageNum, pageSize, keyword, scenicId, type,
                priority, status, sentiment, startDate, endDate));
    }

    @Operation(summary = "查询工单详情", description = "含全部回复记录")
    @RequirePerm("complaint:list")
    @GetMapping("/{id}")
    public Result<ComplaintDetailVO> detail(@PathVariable Long id) {
        return Result.success(complaintService.getDetail(id));
    }

    @Operation(summary = "回复工单")
    @RequirePerm("complaint:reply")
    @OperLog(title = "投诉工单管理", businessType = "UPDATE")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody ComplaintReplyDTO dto) {
        complaintService.reply(id, dto);
        return Result.success("回复成功", null);
    }

    @Operation(summary = "转派工单")
    @RequirePerm("complaint:assign")
    @OperLog(title = "投诉工单管理", businessType = "UPDATE")
    @PutMapping("/assign")
    public Result<Void> assign(@Valid @RequestBody ComplaintAssignDTO dto) {
        complaintService.assign(dto);
        return Result.success("转派成功", null);
    }

    @Operation(summary = "AI 情感分析", description = "负面工单会自动升级为紧急")
    @RequirePerm("complaint:sentiment")
    @OperLog(title = "投诉工单管理", businessType = "UPDATE")
    @PostMapping("/{id}/sentiment")
    public Result<ComplaintVO> analyzeSentiment(@PathVariable Long id) {
        return Result.success("分析完成", complaintService.analyzeSentiment(id));
    }

    @Operation(summary = "批量归纳汇总", description = "传 ids 归纳指定工单；不传则归纳当前景区待处理工单")
    @RequirePerm("complaint:summary")
    @PostMapping("/summary")
    public Result<ComplaintSummaryVO> summarize(@RequestBody(required = false) ComplaintSummaryDTO dto) {
        return Result.success(complaintService.summarize(dto == null ? new ComplaintSummaryDTO() : dto));
    }
}
