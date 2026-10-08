package com.ikun.controller;

import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.AppComplaintDTO;
import com.ikun.dto.AppComplaintReplyDTO;
import com.ikun.service.AppComplaintService;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游客小程序投诉工单接口（对应小程序：投诉建议、我的工单）
 *
 * @author smart-scenic
 */
@Tag(name = "小程序-05-投诉工单", description = "提交投诉/建议/咨询、我的工单、追加工单回复")
@RestController
@RequestMapping("/app/complaint")
@RequiredArgsConstructor
public class AppComplaintController {

    private final AppComplaintService appComplaintService;

    @Operation(summary = "提交工单", description = "提交时自动做情感分析，负面工单自动升级为紧急")
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody AppComplaintDTO dto) {
        appComplaintService.submit(dto);
        return Result.success("提交成功，我们会尽快处理", null);
    }

    @Operation(summary = "我的工单")
    @GetMapping("/my")
    public Result<PageResult<ComplaintVO>> myComplaints(@RequestParam(defaultValue = "1") Integer pageNum,
                                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                                        @RequestParam(required = false) String status) {
        return Result.success(appComplaintService.myComplaints(pageNum, pageSize, status));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<ComplaintDetailVO> detail(@PathVariable Long id) {
        return Result.success(appComplaintService.myComplaintDetail(id));
    }

    @Operation(summary = "追加工单回复")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @Valid @RequestBody AppComplaintReplyDTO dto) {
        appComplaintService.appendReply(id, dto.getContent());
        return Result.success("回复成功", null);
    }
}
