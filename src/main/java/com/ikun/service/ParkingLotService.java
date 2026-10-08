package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.entity.ParkingLot;

import java.util.List;

/**
 * 停车场点位服务
 *
 * @author smart-scenic
 */
public interface ParkingLotService {

    PageResult<ParkingLot> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                     Long scenicId, String status);

    ParkingLot getDetail(Long id);

    /** 按景区查询车场列表，供小程序展示实时车位 */
    List<ParkingLot> listByScenic(Long scenicId);

    void saveParkingLot(ParkingLot parkingLot);

    void updateParkingLot(ParkingLot parkingLot);

    void removeParkingLot(Long id);

    /** 手动修正剩余车位，状态由剩余比例自动推导 */
    void updateFreeSpace(Long id, Integer freeSpace);
}
