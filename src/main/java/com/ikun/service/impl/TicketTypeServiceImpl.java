package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.TicketStock;
import com.ikun.entity.TicketType;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TicketStockMapper;
import com.ikun.mapper.TicketTypeMapper;
import com.ikun.service.AppCacheService;
import com.ikun.service.TicketTypeService;
import com.ikun.util.TimeSlotUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 门票类型服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl extends ServiceImpl<TicketTypeMapper, TicketType> implements TicketTypeService {

    /** 票种类型白名单（0 元票是合法业务：老年票等免票票种，因此只要求售价非负、不要求为正） */
    private static final Set<String> TICKET_TYPES =
            Set.of("ADULT", "CHILD", "STUDENT", "SENIOR", "FAMILY", "PACKAGE", "GROUP");

    private final TicketOrderMapper ticketOrderMapper;
    private final TicketStockMapper ticketStockMapper;
    private final AppCacheService appCacheService;

    @Override
    public PageResult<TicketType> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                            Long scenicId, String ticketType, Integer status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<TicketType>lambdaQuery()
                .eq(scopeScenicId != null, TicketType::getScenicId, scopeScenicId)
                .like(StringUtils.hasText(keyword), TicketType::getTicketName, keyword)
                .eq(StringUtils.hasText(ticketType), TicketType::getTicketType, ticketType)
                .eq(status != null, TicketType::getStatus, status)
                .orderByAsc(TicketType::getScenicId)
                .orderByAsc(TicketType::getId);
        return PageResult.of(this.page(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public TicketType getDetail(Long id) {
        TicketType type = getById(id);
        if (type == null) {
            throw new BusinessException("票种不存在");
        }
        return type;
    }

    @Override
    public List<TicketType> listOptions(Long scenicId, Integer status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        return this.list(Wrappers.<TicketType>lambdaQuery()
                .eq(scopeScenicId != null, TicketType::getScenicId, scopeScenicId)
                .eq(status != null, TicketType::getStatus, status)
                .orderByAsc(TicketType::getScenicId)
                .orderByAsc(TicketType::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTicketType(TicketType ticketType) {
        // 入库前清掉客户端可控字段，防止伪造创建时间或指定 ID 覆盖历史数据
        ticketType.setId(null);
        ticketType.setCreateTime(null);
        ticketType.setUpdateTime(null);

        Long scenicId = ScenicScope.fillWritable(ticketType.getScenicId());
        if (scenicId == null) {
            throw new BusinessException("请选择所属景区");
        }
        ticketType.setScenicId(scenicId);
        validatePrice(ticketType);
        assertTicketNameUnique(ticketType.getTicketName(), scenicId, ticketType.getTicketType(), null);
        if (ticketType.getStatus() == null) {
            ticketType.setStatus(1);
        }
        validateTimeSlots(ticketType.getTimeSlots());
        save(ticketType);
        appCacheService.evictScenic(scenicId);
        log.info("新增票种成功：scenicId={}，ticketName={}", scenicId, ticketType.getTicketName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTicketType(TicketType ticketType) {
        if (ticketType.getId() == null) {
            throw new BusinessException("票种ID不能为空");
        }
        TicketType exist = getDetail(ticketType.getId());
        ScenicScope.checkWritable(exist.getScenicId());
        ticketType.setScenicId(exist.getScenicId());
        ticketType.setCreateTime(null);
        ticketType.setUpdateTime(null);
        validatePrice(ticketType);
        assertTicketNameUnique(ticketType.getTicketName(), exist.getScenicId(),
                ticketType.getTicketType(), ticketType.getId());
        validateTimeSlots(ticketType.getTimeSlots());
        updateById(ticketType);
        appCacheService.evictScenic(exist.getScenicId());
        log.info("编辑票种成功：id={}", ticketType.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTicketType(Long id) {
        TicketType type = getDetail(id);
        ScenicScope.checkWritable(type.getScenicId());
        // 票种被订单引用后删除，会让历史订单的 ticket_type_id 变成悬空引用，
        // 订单详情页取不到票种名称。下架（changeStatus）才是正确的业务动作。
        Long referenced = ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getTicketTypeId, id));
        if (referenced != null && referenced > 0) {
            throw new BusinessException("该票种已被 " + referenced + " 笔订单引用，无法删除，建议改为下架");
        }
        // 票种删除后其每日库存行会变成孤儿数据（AppScenicServiceImpl 按 ticketTypeId 查库存），
        // 这里同步清掉，避免库存表出现查不到票种的悬空记录。
        ticketStockMapper.delete(Wrappers.<TicketStock>lambdaQuery()
                .eq(TicketStock::getTicketTypeId, id));
        removeById(id);
        appCacheService.evictScenic(type.getScenicId());
        log.info("删除票种成功：id={}，ticketName={}", id, type.getTicketName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不合法，只能是 0（下架）或 1（上架）");
        }
        TicketType type = getDetail(id);
        ScenicScope.checkWritable(type.getScenicId());
        TicketType update = new TicketType();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
        // 上下架直接影响移动端可售票种列表
        appCacheService.evictScenic(type.getScenicId());
        log.info("票种状态变更：id={}，status={}", id, status);
    }

    /* ==================== 私有方法 ==================== */

    private void validatePrice(TicketType ticketType) {
        // 票种名称与类型是字典字段：名称为空会让订单详情取不到票名，
        // 类型写错会让移动端按类型筛选时查不到该票种
        if (!StringUtils.hasText(ticketType.getTicketName())) {
            throw new BusinessException("请填写票种名称");
        }
        if (ticketType.getTicketName().length() > 60) {
            throw new BusinessException("票种名称不能超过 60 个字符");
        }
        if (StringUtils.hasText(ticketType.getTicketType())
                && !TICKET_TYPES.contains(ticketType.getTicketType())) {
            throw new BusinessException("票种类型只能是 " + String.join("/", TICKET_TYPES));
        }
        if (ticketType.getPrice() == null) {
            throw new BusinessException("请填写票种售价");
        }
        if (ticketType.getPrice().signum() < 0) {
            throw new BusinessException("票种售价不能为负数");
        }
        // 原价只用于展示划线价，低于售价会让页面显示「先涨后降」，属于数据错误
        if (ticketType.getOriginalPrice() != null
                && ticketType.getOriginalPrice().compareTo(ticketType.getPrice()) < 0) {
            throw new BusinessException("原价不能低于售价");
        }
        if (ticketType.getStock() != null && ticketType.getStock() < 0) {
            throw new BusinessException("每日库存不能为负数");
        }
    }

    /**
     * 校验分时时段配置：每个时段必须是 {@code HH:mm-HH:mm} 且开始早于结束，相互不重复。
     *
     * <p>配置错了会导致游客下单时「选了时段却查不到库存」，属于必须在写入侧拦住的数据错误。</p>
     */
    private void validateTimeSlots(String timeSlots) {
        List<String> slots = TimeSlotUtil.parse(timeSlots);
        if (slots.isEmpty()) {
            return;
        }
        if (slots.size() > 12) {
            throw new BusinessException("分时时段最多配置 12 个");
        }
        Pattern pattern = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d-([01]\\d|2[0-3]):[0-5]\\d$");
        Set<String> seen = new LinkedHashSet<>();
        for (String slot : slots) {
            if (!pattern.matcher(slot).matches()) {
                throw new BusinessException("时段格式不正确：" + slot + "，应形如 08:00-10:00");
            }
            String[] parts = slot.split("-");
            if (parts[0].compareTo(parts[1]) >= 0) {
                throw new BusinessException("时段开始时间须早于结束时间：" + slot);
            }
            if (!seen.add(slot)) {
                throw new BusinessException("时段重复：" + slot);
            }
        }
    }

    private void assertTicketNameUnique(String ticketName, Long scenicId, String ticketType, Long excludeId) {
        Long count = this.count(Wrappers.<TicketType>lambdaQuery()
                .eq(TicketType::getScenicId, scenicId)
                .eq(StringUtils.hasText(ticketType), TicketType::getTicketType, ticketType)
                .eq(StringUtils.hasText(ticketName), TicketType::getTicketName, ticketName)
                .ne(excludeId != null, TicketType::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("同景区下已存在同名同类型的票种：" + ticketName);
        }
    }
}
