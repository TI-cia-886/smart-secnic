package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.ScenicSpot;
import com.ikun.mapper.FlowWarningMapper;
import com.ikun.mapper.ScenicSpotMapper;
import com.ikun.service.ScenicSpotService;
import com.ikun.util.CodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 景点服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
public class ScenicSpotServiceImpl extends ServiceImpl<ScenicSpotMapper, ScenicSpot> implements ScenicSpotService {

    /** 景点类型白名单 */
    private static final Set<String> SPOT_TYPES = Set.of("NATURAL", "CULTURAL", "PLAY");
    /** 景点状态白名单 */
    private static final Set<String> SPOT_STATUS = Set.of("OPEN", "MAINTENANCE", "CLOSED");

    // 该服务未用构造器注入（MyBatis-Plus ServiceImpl 走默认构造），这里用字段注入保持原样
    @Autowired
    private CodeGenerator codeGenerator;
    @Autowired
    private FlowWarningMapper flowWarningMapper;

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
        validateSpot(spot);
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
        validateSpot(spot);
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
        // 该景点还有未处理的客流预警：预警里的 spot_name 是快照，景点删除后预警失去处置对象，
        // 值班人员无法对应到现场位置。先处置预警再删景点。
        long unhandled = flowWarningMapper.selectCount(Wrappers.<FlowWarning>lambdaQuery()
                .eq(FlowWarning::getSpotId, id)
                .eq(FlowWarning::getStatus, "UNHANDLED"));
        if (unhandled > 0) {
            throw new BusinessException("该景点还有 " + unhandled + " 条未处理的客流预警，请先处置后再删除");
        }
        removeById(id);
        log.info("删除景点成功：id={}，spotName={}", id, spot.getSpotName());
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 景点关键字段校验。
     *
     * <p>Controller 直接用实体接参且没有校验注解，瞬时承载量、票价、类型、状态
     * 都必须在这里兜住：瞬时承载量为 0 或负数会让客流预警计算失真，
     * 票价为负会生成倒贴钱的订单。</p>
     */
    private void validateSpot(ScenicSpot spot) {
        if (!StringUtils.hasText(spot.getSpotName())) {
            throw new BusinessException("景点名称不能为空");
        }
        if (spot.getSpotName().length() > 60) {
            throw new BusinessException("景点名称长度不能超过 60 个字符");
        }
        if (spot.getInstantCapacity() == null || spot.getInstantCapacity() <= 0) {
            throw new BusinessException("瞬时承载量必须大于 0");
        }
        if (spot.getSuggestDuration() != null && spot.getSuggestDuration() < 0) {
            throw new BusinessException("建议游览时长不能为负数");
        }
        if (spot.getPrice() != null && spot.getPrice().signum() < 0) {
            throw new BusinessException("景点票价不能为负数");
        }
        if (StringUtils.hasText(spot.getSpotType()) && !SPOT_TYPES.contains(spot.getSpotType())) {
            throw new BusinessException("景点类型只能是 " + String.join("/", SPOT_TYPES));
        }
        if (StringUtils.hasText(spot.getStatus()) && !SPOT_STATUS.contains(spot.getStatus())) {
            throw new BusinessException("景点状态只能是 " + String.join("/", SPOT_STATUS));
        }
    }

    /**
     * 生成景点编号：SP + 景区ID + 4 位流水，如 SP10001（1 号景区第 1 个景点）。
     *
     * <p>景点是长期资产，编号里带创建日期反而是噪声，所以走「范围 + 流水」而非按天流水。
     * 带景区ID是为了多景区下不同景区的景点不会重号。</p>
     */
    private String generateSpotCode(Long scenicId) {
        String code;
        int retry = 0;
        do {
            code = codeGenerator.entity(CodeGenerator.PREFIX_SPOT, scenicId);
            // Redis 降级时流水是随机值，理论上有极小概率撞已有编号，查库确认后重试
        } while (++retry < 5
                && this.count(Wrappers.<ScenicSpot>lambdaQuery().eq(ScenicSpot::getSpotCode, code)) > 0);
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
