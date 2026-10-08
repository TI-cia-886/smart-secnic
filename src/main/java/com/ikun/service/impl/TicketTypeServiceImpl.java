package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.TicketType;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TicketTypeMapper;
import com.ikun.service.TicketTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 门票类型服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl extends ServiceImpl<TicketTypeMapper, TicketType> implements TicketTypeService {

    private final TicketOrderMapper ticketOrderMapper;

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
        save(ticketType);
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
        updateById(ticketType);
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
        removeById(id);
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
        log.info("票种状态变更：id={}，status={}", id, status);
    }

    /* ==================== 私有方法 ==================== */

    private void validatePrice(TicketType ticketType) {
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
