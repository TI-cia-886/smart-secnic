package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.dto.AppComplaintDTO;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintVO;

/**
 * 游客小程序工单服务
 *
 * @author smart-scenic
 */
public interface AppComplaintService {

    /** 提交工单，提交时自动做一次情感分析 */
    void submit(AppComplaintDTO dto);

    /** 我的工单 */
    PageResult<ComplaintVO> myComplaints(Integer pageNum, Integer pageSize, String status);

    /** 我的工单详情 */
    ComplaintDetailVO myComplaintDetail(Long id);

    /** 游客追加回复 */
    void appendReply(Long id, String content);
}
