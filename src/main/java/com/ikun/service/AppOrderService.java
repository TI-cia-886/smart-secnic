package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.AppOrderCreateDTO;
import com.ikun.dto.AppRefundDTO;
import com.ikun.vo.TicketOrderVO;

/**
 * 游客小程序订单服务
 *
 * @author smart-scenic
 */
public interface AppOrderService {

    /** 下单：校验票种、游玩日期与库存后生成订单 */
    TicketOrderVO create(AppOrderCreateDTO dto);

    /** 支付订单（演示环境为模拟支付），成功后锁定库存转为已售 */
    TicketOrderVO pay(Long orderId);

    /** 我的订单 */
    PageResult<TicketOrderVO> myOrders(Integer pageNum, Integer pageSize, String status);

    /** 订单详情：做了归属校验，只能查看自己的订单 */
    TicketOrderVO myOrderDetail(Long orderId);

    /** 申请退票 */
    void applyRefund(Long orderId, AppRefundDTO dto);

    /** 取消未支付订单 */
    void cancel(Long orderId);
}
