package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.entity.ScenicSpot;
import com.ikun.mapper.ScenicSpotMapper;
import com.ikun.service.ScenicSpotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 景点服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
public class ScenicSpotServiceImpl extends ServiceImpl<ScenicSpotMapper, ScenicSpot> implements ScenicSpotService {

    @Override
    public PageResult<ScenicSpot> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                            Long scenicId, String spotType, String status) {
        // 景区过滤统一走 ScenicScope：受限账号忽略前端传值，强制只看本景区
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<ScenicSpot>lambdaQuery()
                .eq(scopeScenicId != null, ScenicSpot::getScenicId, scopeScenicId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(ScenicSpot::getSpotName, keyword).or()
                        .like(ScenicSpot::getSpotCode, keyword))
                .eq(StringUtils.hasText(spotType), ScenicSpot::getSpotType, spotType)
                .eq(StringUtils.hasText(status), ScenicSpot::getStatus, status)
                .orderByAsc(ScenicSpot::getScenicId)
                .orderByAsc(ScenicSpot::getSpotCode);
        return PageResult.of(this.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public ScenicSpot getDetail(Long id) {
        ScenicSpot spot = getById(id);
        if (spot == null) {
            throw new BusinessException("景点不存在");
        }
        return spot;
    }

    @Override
    public List<ScenicSpot> listOptions(Long scenicId) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        return this.list(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(scopeScenicId != null, ScenicSpot::getScenicId, scopeScenicId)
                .orderByAsc(ScenicSpot::getScenicId)
                .orderByAsc(ScenicSpot::getSpotCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpot(ScenicSpot spot) {
        // 请求体直接复用实体，因此在入库前必须清掉客户端可控的危险字段：
        // 否则前端可以伪造 createTime、把 deleted 置 1 造成「幽灵数据」，
        // 或直接指定 id 覆盖已有记录
        spot.setId(null);
        spot.setCreateTime(null);
        spot.setUpdateTime(null);
        spot.setDeleted(null);
        spot.setCurrentCount(0);

        Long scenicId = ScenicScope.fillWritable(spot.getScenicId());
        if (scenicId == null) {
            throw new BusinessException("请选择所属景区");
        }
        spot.setScenicId(scenicId);
        if (!StringUtils.hasText(spot.getSpotCode())) {
            spot.setSpotCode(generateSpotCode(scenicId));
        } else {
            assertSpotCodeUnique(spot.getSpotCode(), scenicId, null);
        }
        if (spot.getStatus() == null) {
            spot.setStatus("OPEN");
        }
        save(spot);
        log.info("新增景点成功：scenicId={}，spotCode={}", scenicId, spot.getSpotCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSpot(ScenicSpot spot) {
        if (spot.getId() == null) {
            throw new BusinessException("景点ID不能为空");
        }
        ScenicSpot exist = getDetail(spot.getId());
        // 不允许把景点迁到别的景区：历史订单、工单都按 scenicId 归档，迁移会造成数据错位
        ScenicScope.checkWritable(exist.getScenicId());
        spot.setScenicId(exist.getScenicId());
        spot.setCreateTime(null);
        spot.setUpdateTime(null);
        spot.setDeleted(null);
        spot.setCurrentCount(null);
        if (StringUtils.hasText(spot.getSpotCode())) {
            assertSpotCodeUnique(spot.getSpotCode(), exist.getScenicId(), spot.getId());
        }
        updateById(spot);
        log.info("编辑景点成功：id={}", spot.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeSpot(Long id) {
        ScenicSpot spot = getDetail(id);
        ScenicScope.checkWritable(spot.getScenicId());
        removeById(id);
        log.info("删除景点成功：id={}，spotName={}", id, spot.getSpotName());
    }

    /* ==================== 私有方法 ==================== */

    /** 生成景点编号：SP- + 景区ID + 4 位流水，避免与其他景区重号 */
    private String generateSpotCode(Long scenicId) {
        Long count = this.count(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getScenicId, scenicId));
        long seq = (count == null ? 0 : count) + 1;
        String code;
        do {
            code = String.format("SP-%d%04d", scenicId, seq++);
        } while (this.count(Wrappers.<ScenicSpot>lambdaQuery().eq(ScenicSpot::getSpotCode, code)) > 0);
        return code;
    }

    private void assertSpotCodeUnique(String spotCode, Long scenicId, Long excludeId) {
        Long count = this.count(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getSpotCode, spotCode)
                .eq(ScenicSpot::getScenicId, scenicId)
                .ne(excludeId != null, ScenicSpot::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("景点编号已存在：" + spotCode);
        }
    }
}
