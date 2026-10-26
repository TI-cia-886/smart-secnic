package com.ikun.controller;

import com.alibaba.excel.EasyExcel;
import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.TouristBlacklistDTO;
import com.ikun.dto.TouristImportResultVO;
import com.ikun.entity.Tourist;
import com.ikun.service.TouristService;
import com.ikun.vo.TouristExportVO;
import com.ikun.vo.TouristStatVO;
import com.ikun.vo.TouristVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 游客信息管理接口（对应原型：游客信息管理页面）
 *
 * @author smart-scenic
 */
@Tag(name = "08-游客信息管理", description = "游客档案查询、黑名单管理、注册统计")
@RestController
@RequestMapping("/tourist")
@RequiredArgsConstructor
public class TouristController {

    /** 导入文件允许的格式 */
    private static final Set<String> ALLOWED_IMPORT_EXT = Set.of("xlsx", "xls");
    /** 导入文件大小上限：10MB */
    private static final long MAX_IMPORT_SIZE = 10 * 1024 * 1024L;

    private final TouristService touristService;

    @Operation(summary = "分页查询游客列表")
    @RequirePerm("tourist:list")
    @GetMapping("/page")
    public Result<PageResult<TouristVO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long scenicId,
                                              @RequestParam(required = false) Integer isBlacklist,
                                              @RequestParam(required = false) Integer realNameStatus,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(touristService.pageQuery(pageNum, pageSize, keyword, scenicId,
                isBlacklist, realNameStatus, status));
    }

    @Operation(summary = "查询游客详情")
    @RequirePerm("tourist:list")
    @GetMapping("/{id}")
    public Result<TouristVO> detail(@PathVariable Long id) {
        return Result.success(touristService.getDetail(id));
    }

    @Operation(summary = "编辑游客信息", description = "仅支持修改备注、会员等级与账号状态")
    @RequirePerm("tourist:list")
    @OperLog(title = "游客信息管理", businessType = "UPDATE")
    @PutMapping
    public Result<Void> update(@RequestBody Tourist tourist) {
        touristService.updateTourist(tourist);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "加入黑名单")
    @RequirePerm("tourist:blacklist")
    @OperLog(title = "游客信息管理", businessType = "UPDATE")
    @PostMapping("/blacklist")
    public Result<Void> addToBlacklist(@Valid @RequestBody TouristBlacklistDTO dto) {
        touristService.addToBlacklist(dto);
        return Result.success("已加入黑名单", null);
    }

    @Operation(summary = "移出黑名单")
    @RequirePerm("tourist:blacklist")
    @OperLog(title = "游客信息管理", businessType = "UPDATE")
    @DeleteMapping("/blacklist/{id}")
    public Result<Void> removeFromBlacklist(@PathVariable Long id) {
        touristService.removeFromBlacklist(id);
        return Result.success("已移出黑名单", null);
    }

    @Operation(summary = "游客注册统计", description = "含注册趋势、会员等级/来源/性别分布，默认统计近 30 天")
    @RequirePerm("tourist:stat")
    @GetMapping("/statistics")
    public Result<TouristStatVO> statistics(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(touristService.statistics(scenicId, startDate, endDate));
    }

    /* ==================== 导入 / 导出 ==================== */

    @Operation(summary = "导出游客 Excel", description = "最多 50000 条，手机号 / 证件号已脱敏")
    @RequirePerm("tourist:export")
    @OperLog(title = "游客信息管理", businessType = "EXPORT")
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long scenicId,
                       @RequestParam(required = false) Integer isBlacklist,
                       @RequestParam(required = false) Integer realNameStatus,
                       @RequestParam(required = false) Integer status)
            throws IOException {
        List<TouristExportVO> list = touristService.listForExport(keyword, scenicId,
                isBlacklist, realNameStatus, status, 50000);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("游客数据", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), TouristExportVO.class)
                .sheet("游客数据")
                .doWrite(list);
    }

    @Operation(summary = "导入游客 Excel",
            description = "逐行校验，重复邮箱跳过、单行失败不影响其他行；返回成功条数 / 失败明细")
    @RequirePerm("tourist:import")
    @OperLog(title = "游客信息管理", businessType = "IMPORT")
    @PostMapping("/import")
    public Result<TouristImportResultVO> importExcel(@RequestPart("file") MultipartFile file) {
        // 导入会逐行写库，不限制文件会让超大/非 Excel 文件把解析线程拖垮
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new BusinessException("导入文件不能超过 10MB");
        }
        String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename()
                        .substring(file.getOriginalFilename().lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_IMPORT_EXT.contains(ext)) {
            throw new BusinessException("仅支持 .xlsx / .xls 格式的 Excel 文件");
        }
        return Result.success("导入完成", touristService.importFromExcel(file));
    }
}
