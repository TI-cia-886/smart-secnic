package com.ikun.controller;

import com.alibaba.excel.EasyExcel;
import com.ikun.annotation.OperLog;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.RefundAuditDTO;
import com.ikun.service.TicketOrderService;
import com.ikun.vo.OrderStatVO;
import com.ikun.vo.TicketOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 订单预约管理接口（对应原型：订单预约管理页面）
 *
 * <p>订单是钱相关的数据，所有状态流转都只在服务端判定，
 * 前端传上来的 status 一律不采信。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "09-订单预约管理", description = "订单查询、核销、退票审核与导出")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class TicketOrderController {

    private final TicketOrderService ticketOrderService;

    @Operation(summary = "分页查询订单列表")
    @RequirePerm("order:list")
    @GetMapping("/page")
    public Result<PageResult<TicketOrderVO>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long ticketTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate playDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate playDateEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createDateEnd) {
        return Result.success(ticketOrderService.pageQuery(pageNum, pageSize, keyword, scenicId, status,
                channel, ticketTypeId, playDateStart, playDateEnd, createDateStart, createDateEnd));
    }

    @Operation(summary = "查询订单详情")
    @RequirePerm("order:list")
    @GetMapping("/{id}")
    public Result<TicketOrderVO> detail(@PathVariable Long id) {
        return Result.success(ticketOrderService.getDetail(id));
    }

    @Operation(summary = "订单统计", description = "含订单量/营收趋势与状态、渠道分布，默认统计近 30 天")
    @RequirePerm("order:list")
    @GetMapping("/statistics")
    public Result<OrderStatVO> statistics(
            @RequestParam(required = false) Long scenicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(ticketOrderService.statistics(scenicId, startDate, endDate));
    }

    @Operation(summary = "订单核销", description = "仅已支付且已到游玩日期的订单可核销")
    @RequirePerm("order:verify")
    @OperLog(title = "订单预约管理", businessType = "UPDATE")
    @PutMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id, @RequestParam(required = false) String gate) {
        ticketOrderService.verify(id, gate);
        return Result.success("核销成功", null);
    }

    @Operation(summary = "扫码核销", description = "code 为电子票二维码内容，也支持直接传订单号")
    @RequirePerm("order:verify")
    @OperLog(title = "订单预约管理", businessType = "UPDATE")
    @PutMapping("/verify-by-code")
    public Result<Void> verifyByCode(@RequestParam String code,
                                     @RequestParam(required = false) String gate) {
        ticketOrderService.verifyByCode(code, gate);
        return Result.success("核销成功", null);
    }

    @Operation(summary = "退票审核", description = "通过则退款并回滚库存，驳回时审核意见必填")
    @RequirePerm("order:refund")
    @OperLog(title = "订单预约管理", businessType = "UPDATE")
    @PutMapping("/refund/audit")
    public Result<Void> auditRefund(@Valid @RequestBody RefundAuditDTO dto) {
        ticketOrderService.auditRefund(dto);
        return Result.success(Boolean.TRUE.equals(dto.getApproved()) ? "退票已通过" : "退票已驳回", null);
    }

    @Operation(summary = "导出订单 Excel")
    @RequirePerm("order:export")
    @OperLog(title = "订单预约管理", businessType = "EXPORT")
    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long scenicId,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String channel,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate playDateStart,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate playDateEnd)
            throws IOException {
        // 导出接口不走统一响应体，直接把文件写进响应流；
        // 文件名用 RFC 5987 编码，避免中文名在各浏览器下变成乱码
        List<TicketOrderVO> list = ticketOrderService.listForExport(keyword, scenicId, status, channel,
                playDateStart, playDateEnd, 5000);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("订单数据", StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), TicketOrderVO.class)
                .sheet("订单数据")
                .doWrite(list);
    }
}
