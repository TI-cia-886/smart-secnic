package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.AiChatDTO;
import com.ikun.dto.AiGenerateDTO;
import com.ikun.entity.AiChat;
import com.ikun.entity.AiKnowledge;
import com.ikun.entity.AiLog;
import com.ikun.service.AiService;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AiOverviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * AI 智能管理接口（对应原型：AI 智能管理页面）
 *
 * @author smart-scenic
 */
@Tag(name = "13-AI 智能管理", description = "知识库、问答、情感分析、公告草稿与疏导预案")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /* ==================== 概览 ==================== */

    @Operation(summary = "AI 服务概览", description = "调用量、成功率、平均耗时、知识库命中率与近 7 天趋势")
    @RequirePerm("ai:manage:list")
    @GetMapping("/overview")
    public Result<AiOverviewVO> overview(@RequestParam(required = false) Long scenicId) {
        return Result.success(aiService.overview(scenicId));
    }

    /* ==================== 知识库 ==================== */

    @Operation(summary = "分页查询知识库")
    @RequirePerm("ai:manage:list")
    @GetMapping("/knowledge/page")
    public Result<PageResult<AiKnowledge>> knowledgePage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status) {
        return Result.success(aiService.pageKnowledge(pageNum, pageSize, keyword, scenicId, category, status));
    }

    @Operation(summary = "新增知识库条目")
    @RequirePerm("ai:knowledge:edit")
    @OperLog(title = "AI 智能管理", businessType = "INSERT")
    @PostMapping("/knowledge")
    public Result<Void> saveKnowledge(@RequestBody AiKnowledge knowledge) {
        aiService.saveKnowledge(knowledge);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑知识库条目")
    @RequirePerm("ai:knowledge:edit")
    @OperLog(title = "AI 智能管理", businessType = "UPDATE")
    @PutMapping("/knowledge")
    public Result<Void> updateKnowledge(@RequestBody AiKnowledge knowledge) {
        aiService.updateKnowledge(knowledge);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除知识库条目")
    @RequirePerm("ai:knowledge:edit")
    @OperLog(title = "AI 智能管理", businessType = "DELETE")
    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        aiService.removeKnowledge(id);
        return Result.success("删除成功", null);
    }

    /* ==================== 问答 ==================== */

    @Operation(summary = "智能问答", description = "优先命中知识库，未命中时由模型生成；多轮对话请回传 sessionId")
    @RequirePerm("ai:chat:list")
    @PostMapping("/chat")
    public Result<AiChatReplyVO> chat(@Valid @RequestBody AiChatDTO dto) {
        return Result.success(aiService.chat(dto));
    }

    @Operation(summary = "分页查询会话记录")
    @RequirePerm("ai:chat:list")
    @GetMapping("/chat/page")
    public Result<PageResult<AiChat>> chatPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long touristId) {
        return Result.success(aiService.pageChat(pageNum, pageSize, scenicId, sessionId, touristId));
    }

    /* ==================== 调用日志 ==================== */

    @Operation(summary = "分页查询 AI 调用日志")
    @RequirePerm("ai:log:list")
    @GetMapping("/log/page")
    public Result<PageResult<AiLog>> logPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status) {
        return Result.success(aiService.pageLog(pageNum, pageSize, scenicId, module, status));
    }

    /* ==================== 内容生成 ==================== */

    @Operation(summary = "生成公告草稿", description = "生成结果需人工核实后再发布")
    @RequirePerm("ai:announcement:draft")
    @OperLog(title = "AI 智能管理", businessType = "INSERT")
    @PostMapping("/announcement/draft")
    public Result<String> announcementDraft(@Valid @RequestBody AiGenerateDTO dto) {
        dto.setScene("ANNOUNCEMENT");
        return Result.success("生成成功", aiService.generate(dto));
    }

    @Operation(summary = "生成客流疏导预案", description = "可基于某条超载预警生成，结果会回写到该预警记录")
    @RequirePerm("ai:flow:plan")
    @OperLog(title = "AI 智能管理", businessType = "INSERT")
    @PostMapping("/flow-plan")
    public Result<String> flowPlan(@Valid @RequestBody AiGenerateDTO dto) {
        dto.setScene("FLOW_PLAN");
        return Result.success("生成成功", aiService.generate(dto));
    }
}
