package com.ikun.controller;

import com.ikun.common.PageResult;
import com.ikun.common.Result;
import com.ikun.dto.AppOrderCreateDTO;
import com.ikun.dto.AppRefundDTO;
import com.ikun.service.AppOrderService;
import com.ikun.vo.TicketOrderVO;
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
 * 游客小程序订单接口（对应小程序：购票下单、我的订单、订单详情、退票）
 *
 * <p>整个 {@code /app/order/**} 都要求游客登录，订单归属在服务端校验。</p>
 *
 * @author smart-scenic
 */
@Tag(name = "小程序-03-订单", description = "下单、模拟支付、我的订单、退票、取消订单")
@RestController
@RequestMapping("/app/order")
@RequiredArgsConstructor
public class AppOrderController {

    private final AppOrderService appOrderService;

    @Operation(summary = "创建订单")
    @PostMapping
    public Result<TicketOrderVO> create(@Valid @RequestBody AppOrderCreateDTO dto) {
        return Result.success("下单成功", appOrderService.create(dto));
    }

    @Operation(summary = "支付订单", description = "演示环境为模拟支付，成功后锁定库存转为已售")
    @PostMapping("/{id}/pay")
    public Result<TicketOrderVO> pay(@PathVariable Long id) {
        return Result.success("支付成功", appOrderService.pay(id));
    }

    @Operation(summary = "我的订单")
    @GetMapping("/my")
    public Result<PageResult<TicketOrderVO>> myOrders(@RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                                      @RequestParam(required = false) String status) {
        return Result.success(appOrderService.myOrders(pageNum, pageSize, status));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<TicketOrderVO> detail(@PathVariable Long id) {
        return Result.success(appOrderService.myOrderDetail(id));
    }

    @Operation(summary = "申请退票", description = "仅已支付且未核销的订单可申请")
    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable Long id, @Valid @RequestBody AppRefundDTO dto) {
        appOrderService.applyRefund(id, dto);
        return Result.success("退票申请已提交，请等待审核", null);
    }

    @Operation(summary = "取消订单", description = "仅待支付订单可取消，取消后释放锁定库存")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        appOrderService.cancel(id);
        return Result.success("订单已取消", null);
    }
}
