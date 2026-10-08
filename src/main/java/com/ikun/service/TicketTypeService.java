package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.entity.TicketType;

import java.util.List;

/**
 * 门票类型服务
 *
 * @author smart-scenic
 */
public interface TicketTypeService {

    PageResult<TicketType> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                     Long scenicId, String ticketType, Integer status);

    TicketType getDetail(Long id);

    /** 上架票种下拉项，供订单与游客小程序引用 */
    List<TicketType> listOptions(Long scenicId, Integer status);

    void saveTicketType(TicketType ticketType);

    void updateTicketType(TicketType ticketType);

    /** 删除票种，已被订单引用时拒绝 */
    void removeTicketType(Long id);

    /** 上架/下架 */
    void changeStatus(Long id, Integer status);
}
