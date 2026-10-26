package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.TicketOrder;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.service.AppCacheService;
import com.ikun.service.ScenicAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 景区服务实现
 *
 * @author smart-scenic
 */
@Service
@RequiredArgsConstructor
public class ScenicAreaServiceImpl extends ServiceImpl<ScenicAreaMapper, ScenicArea> implements ScenicAreaService {

    /** 景区等级白名单 */
    private static final Set<String> SCENIC_LEVELS = Set.of("5A", "4A", "3A", "2A", "A");
    /** 景区状态白名单 */
    private static final Set<String> SCENIC_STATUS = Set.of("ENABLE", "DISABLE");

    private final AppCacheService appCacheService;
    private final TicketOrderMapper ticketOrderMapper;

    @Override
    public PageResult<ScenicArea> pageQuery(Integer pageNum, Integer pageSize, String keyword,
                                            String level, String status) {
        // 关键词同时覆盖编码 / 名称 / 城市 / 负责人 / 地址，与前端搜索框提示保持一致
        LambdaQueryWrapper<ScenicArea> wrapper = Wrappers.<ScenicArea>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(ScenicArea::getScenicName, keyword).or()
                        .like(ScenicArea::getScenicCode, keyword).or()
                        .like(ScenicArea::getCity, keyword).or()
                        .like(ScenicArea::getManager, keyword).or()
                        .like(ScenicArea::getAddress, keyword))
                .eq(StringUtils.hasText(level), ScenicArea::getLevel, level)
                .eq(StringUtils.hasText(status), ScenicArea::getStatus, status)
                .orderByAsc(ScenicArea::getScenicCode);
        Page<ScenicArea> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page);
    }

    @Override
    public List<ScenicArea> listOptions() {
        // 景区管理员等受限用户：只返回本景区（即使景区被禁用也返回，保证其后台可用）
        if (ScenicScope.isRestricted()) {
            return this.list(Wrappers.<ScenicArea>lambdaQuery()
                    .eq(ScenicArea::getId, UserContext.getScenicId()));
        }
        return this.list(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, "ENABLE")
                .orderByAsc(ScenicArea::getScenicCode));
    }

    /* ==================== 写操作：同步清除移动端缓存 ==================== */

    @Override
    public void saveScenicArea(ScenicArea scenicArea) {
        validateScenicArea(scenicArea);
        save(scenicArea);
        // 景区列表带筛选参数，key 不固定，统一清掉全部列表缓存
        appCacheService.evictScenic(null);
    }

    @Override
    public void updateScenicArea(ScenicArea scenicArea) {
        validateScenicArea(scenicArea);
        updateById(scenicArea);
        appCacheService.evictScenic(scenicArea.getId());
    }

    @Override
    public void removeScenicArea(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("景区不存在");
        }
        // 存在未来未使用的已购票（含退款中）时禁止删除：必须游客先退票并完成退款
        // （订单状态变为 REFUNDED）后才能删除，避免景区消失后游客已购的有效票
        // 既无法入园、也无法退款。REFUNDING 同样要拦——退款流程还没走完，
        // 景区没了这笔钱就退不回来。
        long futurePaid = ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getScenicId, id)
                .in(TicketOrder::getStatus, List.of("PAID", "REFUNDING"))
                .ge(TicketOrder::getPlayDate, LocalDate.now()));
        if (futurePaid > 0) {
            throw new BusinessException("该景区存在未来未使用的已购票，请游客退票后再删除");
        }
        removeById(id);
        appCacheService.evictScenic(id);
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 景区关键字段校验。
     *
     * <p>Controller 的 {@code @Valid} 对本类无效（ScenicArea 实体没有任何校验注解），
     * 因此入库前必须在 Service 侧兜住：日承载量直接影响客流饱和度计算，
     * 为 0 或负数会让预警阈值失真；等级与状态若不校验，任意字符串都会写进字典字段。</p>
     */
    private void validateScenicArea(ScenicArea scenicArea) {
        if (!StringUtils.hasText(scenicArea.getScenicName())) {
            throw new BusinessException("景区名称不能为空");
        }
        if (scenicArea.getScenicName().length() > 60) {
            throw new BusinessException("景区名称长度不能超过 60 个字符");
        }
        if (StringUtils.hasText(scenicArea.getScenicCode())
                && !scenicArea.getScenicCode().matches("^[A-Za-z0-9_-]{2,30}$")) {
            throw new BusinessException("景区编码只能由 2-30 位字母、数字、下划线或中划线组成");
        }
        if (scenicArea.getDailyCapacity() == null || scenicArea.getDailyCapacity() <= 0) {
            throw new BusinessException("日承载量必须大于 0");
        }
        if (StringUtils.hasText(scenicArea.getLevel())
                && !SCENIC_LEVELS.contains(scenicArea.getLevel())) {
            throw new BusinessException("景区等级只能是 " + String.join("/", SCENIC_LEVELS));
        }
        if (StringUtils.hasText(scenicArea.getStatus())
                && !SCENIC_STATUS.contains(scenicArea.getStatus())) {
            throw new BusinessException("景区状态只能是 " + String.join("/", SCENIC_STATUS));
        }
        if (StringUtils.hasText(scenicArea.getPhone())
                && !scenicArea.getPhone().matches("^[0-9+\\-()\\s]{6,20}$")) {
            throw new BusinessException("联系电话格式不正确");
        }
    }
}
