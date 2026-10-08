package com.ikun.service;

import com.ikun.vo.DashboardVO;

/**
 * 数据概览服务
 *
 * @author smart-scenic
 */
public interface DashboardService {

    /**
     * 首页统计数据
     *
     * @param scenicId 景区ID，为空表示全平台
     */
    DashboardVO stats(Long scenicId);
}
