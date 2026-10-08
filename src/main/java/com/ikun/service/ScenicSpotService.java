package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.entity.ScenicSpot;

import java.util.List;

/**
 * 景点基础数据服务
 *
 * @author smart-scenic
 */
public interface ScenicSpotService {

    PageResult<ScenicSpot> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                     Long scenicId, String spotType, String status);

    ScenicSpot getDetail(Long id);

    /** 下拉选项：返回指定景区下状态为开放的景点，供工单/订单等模块引用 */
    List<ScenicSpot> listOptions(Long scenicId);

    void saveSpot(ScenicSpot spot);

    void updateSpot(ScenicSpot spot);

    /**
     * 删除景点
     *
     * @throws com.ikun.common.BusinessException 当景点下仍有未完成订单时拒绝
     */
    void removeSpot(Long id);
}
