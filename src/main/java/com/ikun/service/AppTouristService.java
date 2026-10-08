package com.ikun.service;

import com.ikun.dto.AppTouristUpdateDTO;
import com.ikun.vo.AppProfileVO;

/**
 * 游客小程序个人中心服务
 *
 * @author smart-scenic
 */
public interface AppTouristService {

    /** 个人中心：基本资料 + 订单/工单统计 */
    AppProfileVO profile();

    /** 修改个人资料 */
    void updateProfile(AppTouristUpdateDTO dto);
}
