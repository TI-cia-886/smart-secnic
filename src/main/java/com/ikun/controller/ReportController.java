package com.ikun.controller;

import com.alibaba.excel.EasyExcel;
import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.Result;
import com.ikun.service.ReportService;
import com.ikun.vo.RevenueReportV2VO;
import com.ikun.vo.TicketReportVO;
import com.ikun.vo.TicketSaleItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 数据报表接口（对应原型：数据报表页面）
 *
 * <p>报表只做「读」和「导出」，不提供任何写操作——
 * 统计口径一旦允许被人工修改，报表就失去了可信度。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "14-数据报表", description = "门票销售报表、营收报表与 Excel 导出")
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "门票销售报表", description = "按票种汇总销量、金额与核销率，默认统计近 30 天")
    @RequirePerm("report:ticket")
    @GetMapping("/ticket")
    public Result<TicketReportVO> ticketReport(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.ticketReport(scenicId, startDate, endDate));
    }

    @Operation(summary = "营收报表", description = "含毛营收、退款金额、净营收、客单价与渠道/景区分布")
    @RequirePerm("report:revenue")
    @GetMapping("/revenue")
    public Result<RevenueReportV2VO> revenueReport(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(reportService.revenueReport(scenicId, startDate, endDate));
    }

    @Operation(summary = "导出票种销售明细 Excel")
    @RequirePerm("report:export")
    @OperLog(title = "数据报表", businessType = "EXPORT")
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) Long scenicId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate)
            throws IOException {
        TicketReportVO report = reportService.ticketReport(scenicId, startDate, endDate);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("门票销售报表_" + report.getStartDate() + "_" + report.getEndDate(),
                StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), TicketSaleItemVO.class)
                .sheet("门票销售报表")
                .doWrite(report.getItems());
    }
}
