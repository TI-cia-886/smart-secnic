package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.RefundAuditDTO;
import com.ikun.vo.OrderStatVO;
import com.ikun.vo.TicketOrderVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单预约服务
 *
 * @author smart-scenic
 */
public interface TicketOrderService {

    PageResult<TicketOrderVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                        String status, String channel, Long ticketTypeId,
                                        LocalDate playDateStart, LocalDate playDateEnd,
                                        LocalDate createDateStart, LocalDate createDateEnd);

    TicketOrderVO getDetail(Long id);

    /** 后台按订单ID核销 */
    void verify(Long orderId, String gate);

    /** 扫码核销：校验二维码签名后核销 */
    void verifyByCode(String code, String gate);

    /** 退票审核：通过则退款并回滚库存，驳回则退回已支付状态 */
    void auditRefund(RefundAuditDTO dto);

    OrderStatVO statistics(Long scenicId, LocalDate startDate, LocalDate endDate);

    /**
     * 导出订单数据
     *
     * @param limit 最大导出条数，防止一次导出把内存打满
     */
    List<TicketOrderVO> listForExport(String keyword, Long scenicId, String status, String channel,
                                      LocalDate playDateStart, LocalDate playDateEnd, int limit);
}
