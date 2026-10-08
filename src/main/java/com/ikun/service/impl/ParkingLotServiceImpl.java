package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.entity.ParkingLot;
import com.ikun.mapper.ParkingLotMapper;
import com.ikun.service.ParkingLotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 停车场点位服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
public class ParkingLotServiceImpl extends ServiceImpl<ParkingLotMapper, ParkingLot> implements ParkingLotService {

    /** 空闲阈值：剩余 > 30% 视为空闲 */
    private static final double FREE_RATIO = 0.3;
    /** 紧张阈值：剩余 > 5% 视为紧张，低于则已满 */
    private static final double BUSY_RATIO = 0.05;

    @Override
    public PageResult<ParkingLot> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                            Long scenicId, String status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<ParkingLot>lambdaQuery()
                .eq(scopeScenicId != null, ParkingLot::getScenicId, scopeScenicId)
                .like(StringUtils.hasText(keyword), ParkingLot::getLotName, keyword)
                .eq(StringUtils.hasText(status), ParkingLot::getStatus, status)
                .orderByAsc(ParkingLot::getScenicId)
                .orderByAsc(ParkingLot::getId);
        return PageResult.of(this.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public ParkingLot getDetail(Long id) {
        ParkingLot lot = getById(id);
        if (lot == null) {
            throw new BusinessException("停车场不存在");
        }
        return lot;
    }

    @Override
    public List<ParkingLot> listByScenic(Long scenicId) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        return this.list(Wrappers.<ParkingLot>lambdaQuery()
                .eq(scopeScenicId != null, ParkingLot::getScenicId, scopeScenicId)
                .orderByAsc(ParkingLot::getScenicId)
                .orderByAsc(ParkingLot::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveParkingLot(ParkingLot parkingLot) {
        parkingLot.setId(null);
        parkingLot.setCreateTime(null);
        parkingLot.setUpdateTime(null);

        Long scenicId = ScenicScope.fillWritable(parkingLot.getScenicId());
        if (scenicId == null) {
            throw new BusinessException("请选择所属景区");
        }
        parkingLot.setScenicId(scenicId);
        validateSpace(parkingLot.getTotalSpace(), parkingLot.getFreeSpace());
        // 剩余车位没填时默认满位，避免出现「总车位 500、剩余 0」的误报拥堵
        if (parkingLot.getFreeSpace() == null) {
            parkingLot.setFreeSpace(parkingLot.getTotalSpace());
        }
        parkingLot.setStatus(deriveStatus(parkingLot.getTotalSpace(), parkingLot.getFreeSpace()));
        save(parkingLot);
        log.info("新增停车场成功：scenicId={}，lotName={}", scenicId, parkingLot.getLotName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateParkingLot(ParkingLot parkingLot) {
        if (parkingLot.getId() == null) {
            throw new BusinessException("停车场ID不能为空");
        }
        ParkingLot exist = getDetail(parkingLot.getId());
        ScenicScope.checkWritable(exist.getScenicId());
        parkingLot.setScenicId(exist.getScenicId());
        parkingLot.setCreateTime(null);
        parkingLot.setUpdateTime(null);

        Integer total = parkingLot.getTotalSpace() == null ? exist.getTotalSpace() : parkingLot.getTotalSpace();
        Integer free = parkingLot.getFreeSpace() == null ? exist.getFreeSpace() : parkingLot.getFreeSpace();
        validateSpace(total, free);
        parkingLot.setTotalSpace(total);
        parkingLot.setFreeSpace(free);
        // 状态由剩余比例推导，不接受前端传值，避免「剩余 500 却显示已满」的矛盾数据
        parkingLot.setStatus(deriveStatus(total, free));
        updateById(parkingLot);
        log.info("编辑停车场成功：id={}", parkingLot.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeParkingLot(Long id) {
        ParkingLot lot = getDetail(id);
        ScenicScope.checkWritable(lot.getScenicId());
        removeById(id);
        log.info("删除停车场成功：id={}，lotName={}", id, lot.getLotName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFreeSpace(Long id, Integer freeSpace) {
        if (freeSpace == null || freeSpace < 0) {
            throw new BusinessException("剩余车位不能为负数");
        }
        ParkingLot lot = getDetail(id);
        ScenicScope.checkWritable(lot.getScenicId());
        if (freeSpace > lot.getTotalSpace()) {
            throw new BusinessException("剩余车位不能超过总车位数 " + lot.getTotalSpace());
        }
        ParkingLot update = new ParkingLot();
        update.setId(id);
        update.setFreeSpace(freeSpace);
        update.setStatus(deriveStatus(lot.getTotalSpace(), freeSpace));
        updateById(update);
        log.info("停车场剩余车位更新：id={}，freeSpace={}", id, freeSpace);
    }

    /* ==================== 私有方法 ==================== */

    private void validateSpace(Integer totalSpace, Integer freeSpace) {
        if (totalSpace == null || totalSpace <= 0) {
            throw new BusinessException("总车位数必须大于 0");
        }
        if (freeSpace != null && freeSpace < 0) {
            throw new BusinessException("剩余车位不能为负数");
        }
        if (freeSpace != null && freeSpace > totalSpace) {
            throw new BusinessException("剩余车位不能超过总车位数");
        }
    }

    /** 由剩余比例推导拥堵状态：FREE 空闲 / BUSY 紧张 / FULL 已满 */
    private String deriveStatus(Integer totalSpace, Integer freeSpace) {
        if (totalSpace == null || totalSpace <= 0 || freeSpace == null) {
            return "FREE";
        }
        double ratio = (double) freeSpace / totalSpace;
        if (ratio <= BUSY_RATIO) {
            return "FULL";
        }
        return ratio <= FREE_RATIO ? "BUSY" : "FREE";
    }
}
