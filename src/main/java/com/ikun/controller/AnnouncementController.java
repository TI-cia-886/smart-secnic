package com.ikun.controller;

import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.entity.Announcement;
import com.ikun.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公告资讯管理接口（对应原型：基础数据 → 公告资讯管理）
 *
 * @author smart-scenic
 */
@Tag(name = "06-公告资讯管理", description = "基础数据 - 公告的增删改查与发布下线")
@RestController
@RequestMapping("/scenic/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "分页查询公告列表")
    @RequirePerm("scenic:announcement:list")
    @GetMapping("/page")
    public Result<PageResult<Announcement>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long scenicId,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) String status) {
        return Result.success(announcementService.pageQuery(pageNum, pageSize, keyword, scenicId, type, status));
    }

    @Operation(summary = "查询公告详情")
    @RequirePerm("scenic:announcement:list")
    @GetMapping("/{id}")
    public Result<Announcement> detail(@PathVariable Long id) {
        return Result.success(announcementService.getDetail(id));
    }

    @Operation(summary = "新增公告")
    @RequirePerm("scenic:announcement:list")
    @OperLog(title = "公告资讯管理", businessType = "INSERT")
    @PostMapping
    public Result<Void> save(@RequestBody Announcement announcement) {
        announcementService.saveAnnouncement(announcement);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "编辑公告")
    @RequirePerm("scenic:announcement:list")
    @OperLog(title = "公告资讯管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@RequestBody Announcement announcement) {
        announcementService.updateAnnouncement(announcement);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "发布公告")
    @RequirePerm("scenic:announcement:list")
    @OperLog(title = "公告资讯管理", businessType = "UPDATE")
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        announcementService.publish(id);
        return Result.success("发布成功", null);
    }

    @Operation(summary = "下线公告")
    @RequirePerm("scenic:announcement:list")
    @OperLog(title = "公告资讯管理", businessType = "UPDATE")
    @PutMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        announcementService.offline(id);
        return Result.success("已下线", null);
    }

    @Operation(summary = "删除公告")
    @RequirePerm("scenic:announcement:list")
    @OperLog(title = "公告资讯管理", businessType = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.removeAnnouncement(id);
        return Result.success("删除成功", null);
    }
}
