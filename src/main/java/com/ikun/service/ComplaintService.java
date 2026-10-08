package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.ComplaintAssignDTO;
import com.ikun.dto.ComplaintReplyDTO;
import com.ikun.dto.ComplaintSummaryDTO;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintSummaryVO;
import com.ikun.vo.ComplaintVO;

import java.time.LocalDate;

/**
 * 投诉工单服务
 *
 * @author smart-scenic
 */
public interface ComplaintService {

    PageResult<ComplaintVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                      String type, String priority, String status, String sentiment,
                                      LocalDate startDate, LocalDate endDate);

    /** 工单详情，含会话式回复记录 */
    ComplaintDetailVO getDetail(Long id);

    /** 客服回复，可顺带完结工单 */
    void reply(Long complaintId, ComplaintReplyDTO dto);

    /** 转派处理人 */
    void assign(ComplaintAssignDTO dto);

    /** 对单条工单执行 AI 情感分析 */
    ComplaintVO analyzeSentiment(Long complaintId);

    /** 批量归纳汇总：统计分布 + AI 生成归纳文字 */
    ComplaintSummaryVO summarize(ComplaintSummaryDTO dto);
}
