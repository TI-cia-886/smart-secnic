package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.FlowWarningHandleDTO;
import com.ikun.entity.CheckinRecord;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.PassengerFlow;
import com.ikun.vo.FlowOverviewVO;
import com.ikun.vo.FlowReportVO;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客流统计服务
 *
 * @author smart-scenic
 */
public interface PassengerFlowService {

    /** 实时概览：当前在园、今日进出、饱和度与各景点明细 */
    FlowOverviewVO overview(Long scenicId, LocalDate date);

    PageResult<PassengerFlow> pageQuery(Integer pageNum, Integer pageSize, Long scenicId,
                                        LocalDate startDate, LocalDate endDate);

    /** 检票核验记录分页 */
    PageResult<CheckinRecord> pageCheckin(Integer pageNum, Integer pageSize, Long scenicId,
                                          String status, String verifyType, String orderNo,
                                          LocalDateTime startTime, LocalDateTime endTime);

    /** 预警记录分页 */
    PageResult<FlowWarning> pageWarning(Integer pageNum, Integer pageSize, Long scenicId,
                                        String status, String warningLevel);

    /** 处理预警 */
    void handleWarning(FlowWarningHandleDTO dto);

    /** 客流数据报表 */
    FlowReportVO report(Long scenicId, LocalDate startDate, LocalDate endDate);
}
