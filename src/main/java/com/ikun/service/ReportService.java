package com.ikun.service;

import com.ikun.vo.RevenueReportV2VO;
import com.ikun.vo.TicketReportVO;

import java.time.LocalDate;

/**
 * 数据报表服务
 *
 * @author smart-scenic
 */
public interface ReportService {

    /**
     * 门票销售报表
     *
     * @param startDate 为空默认近 30 天
     * @param endDate   为空默认今天
     */
    TicketReportVO ticketReport(Long scenicId, LocalDate startDate, LocalDate endDate);

    /** 营收报表：含毛/净营收、客单价、渠道与景区分布 */
    RevenueReportV2VO revenueReport(Long scenicId, LocalDate startDate, LocalDate endDate);
}
