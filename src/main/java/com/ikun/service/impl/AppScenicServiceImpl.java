package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.config.ThreadPoolConfig;
import com.ikun.dto.AiChatDTO;
import com.ikun.entity.Announcement;
import com.ikun.entity.ParkingLot;
import com.ikun.entity.PassengerFlow;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.ScenicSpot;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.TicketStock;
import com.ikun.entity.TicketType;
import com.ikun.mapper.AnnouncementMapper;
import com.ikun.mapper.ParkingLotMapper;
import com.ikun.mapper.PassengerFlowMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.ScenicSpotMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TicketStockMapper;
import com.ikun.mapper.TicketTypeMapper;
import com.ikun.service.AiService;
import com.ikun.service.AppCacheService;
import com.ikun.service.AppScenicService;
import com.ikun.util.TimeSlotUtil;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AppHomeVO;
import com.ikun.vo.AppRouteSpotVO;
import com.ikun.vo.AppRouteVO;
import com.ikun.vo.TicketAvailabilityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 游客小程序浏览服务实现
 *
 * <p>首页把景区、公告、客流、热门景点、停车场五块数据一次返回，
 * 减少小程序首屏的串行请求次数。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppScenicServiceImpl implements AppScenicService {

    private static final String SCENIC_STATUS_ENABLE = "ENABLE";
    private static final String SPOT_STATUS_OPEN = "OPEN";
    private static final String ANNOUNCEMENT_STATUS_PUBLISHED = "PUBLISHED";
    private static final String ORDER_STATUS_PENDING_PAY = "PENDING_PAY";

    private static final double WARNING_THRESHOLD = 70D;
    private static final double DANGER_THRESHOLD = 90D;

    private static final int DEFAULT_ANNOUNCEMENT_LIMIT = 10;
    private static final int MAX_ANNOUNCEMENT_LIMIT = 50;
    private static final int DEFAULT_SPOT_DURATION = 60;
    private static final int SPOT_WALK_MINUTES = 15;

    private final ScenicAreaMapper scenicAreaMapper;
    private final ScenicSpotMapper scenicSpotMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final AnnouncementMapper announcementMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final PassengerFlowMapper passengerFlowMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketStockMapper ticketStockMapper;
    private final AiService aiService;
    private final AppCacheService appCacheService;
    /** AI 外部调用专用线程池，与业务线程隔离 */
    @Qualifier("aiExecutor")
    private final ThreadPoolTaskExecutor aiExecutor;

    @Override
    public AppHomeVO home(Long scenicId) {
        Long target = resolveScenic(scenicId);
        Long touristId = AppUserContext.getTouristId();
        // 登录用户的结果里含「我的待支付订单数」这类个性化数据，不能共享缓存；
        // 未登录游客占了首页请求的绝大多数，缓存这部分就足以显著降低数据库压力。
        if (touristId != null) {
            return buildHome(target, touristId);
        }
        AppHomeVO cached = appCacheService.query(appCacheService.homeKey(target),
                AppCacheService.TTL_HOME, () -> buildHome(target, null));
        if (cached == null) {
            throw new BusinessException("景区不存在");
        }
        return cached;
    }

    private AppHomeVO buildHome(Long target, Long touristId) {
        ScenicArea area = scenicAreaMapper.selectById(target);
        if (area == null) {
            throw new BusinessException("景区不存在");
        }

        AppHomeVO vo = new AppHomeVO();
        vo.setScenicId(area.getId());
        vo.setScenicName(area.getScenicName());
        vo.setLevel(area.getLevel());
        vo.setAddress(area.getAddress());
        vo.setPhone(area.getPhone());
        vo.setCoverImg(area.getCoverImg());
        vo.setDescription(area.getDescription());
        vo.setLongitude(area.getLongitude());
        vo.setLatitude(area.getLatitude());
        vo.setDailyCapacity(area.getDailyCapacity());

        vo.setAnnouncements(announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
                // 全局公告（scenic_id 为 NULL）对所有景区可见
                .and(w -> w.eq(Announcement::getScenicId, target).or().isNull(Announcement::getScenicId))
                .eq(Announcement::getStatus, ANNOUNCEMENT_STATUS_PUBLISHED)
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT 5")));

        fillFlow(vo, target, area.getDailyCapacity());

        vo.setHotSpots(scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getScenicId, target)
                .eq(ScenicSpot::getStatus, SPOT_STATUS_OPEN)
                .orderByDesc(ScenicSpot::getCurrentCount)
                .last("LIMIT 6")));

        vo.setParkingLots(parkingLotMapper.selectList(Wrappers.<ParkingLot>lambdaQuery()
                .eq(ParkingLot::getScenicId, target)
                .orderByAsc(ParkingLot::getId)));

        // 未登录游客返回 0；登录游客展示待支付数量，提醒其尽快完成支付
        vo.setPendingOrderCount(touristId == null ? 0L : ticketOrderMapper.selectCount(
                Wrappers.<TicketOrder>lambdaQuery()
                        .eq(TicketOrder::getTouristId, touristId)
                        .eq(TicketOrder::getStatus, ORDER_STATUS_PENDING_PAY)));
        return vo;
    }

    @Override
    public List<ScenicArea> listScenic(String keyword, String level, String city) {
        // 景区基础信息读多写少，缓存后在管理端改数据时由 AppCacheService.evictScenic 清除
        return appCacheService.query(appCacheService.scenicListKey(keyword, level, city),
                AppCacheService.TTL_SCENIC_LIST, () -> scenicAreaMapper.selectList(
                        Wrappers.<ScenicArea>lambdaQuery()
                                .eq(ScenicArea::getStatus, SCENIC_STATUS_ENABLE)
                                .like(StringUtils.hasText(keyword), ScenicArea::getScenicName, keyword)
                                .eq(StringUtils.hasText(level), ScenicArea::getLevel, level)
                                .eq(StringUtils.hasText(city), ScenicArea::getCity, city)
                                .orderByDesc(ScenicArea::getLevel)
                                .orderByAsc(ScenicArea::getId)));
    }

    @Override
    public ScenicArea scenicDetail(Long scenicId) {
        ScenicArea area = scenicId == null ? null : scenicAreaMapper.selectById(scenicId);
        if (area == null) {
            throw new BusinessException("景区不存在");
        }
        return area;
    }

    @Override
    public List<ScenicSpot> listSpots(Long scenicId, String spotType, String keyword) {
        Long target = resolveScenic(scenicId);
        return scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getScenicId, target)
                .eq(StringUtils.hasText(spotType), ScenicSpot::getSpotType, spotType)
                .like(StringUtils.hasText(keyword), ScenicSpot::getSpotName, keyword)
                .orderByDesc(ScenicSpot::getCurrentCount));
    }

    @Override
    public ScenicSpot spotDetail(Long spotId) {
        ScenicSpot spot = spotId == null ? null : scenicSpotMapper.selectById(spotId);
        if (spot == null) {
            throw new BusinessException("景点不存在");
        }
        return spot;
    }

    @Override
    public List<TicketType> listTickets(Long scenicId) {
        Long target = resolveScenic(scenicId);
        return appCacheService.query(appCacheService.ticketsKey(target),
                AppCacheService.TTL_TICKETS, () -> ticketTypeMapper.selectList(
                        Wrappers.<TicketType>lambdaQuery()
                                .eq(TicketType::getScenicId, target)
                                .eq(TicketType::getStatus, 1)
                                .orderByAsc(TicketType::getPrice)));
    }

    @Override
    public TicketType ticketDetail(Long ticketTypeId) {
        TicketType type = ticketTypeId == null ? null : ticketTypeMapper.selectById(ticketTypeId);
        if (type == null) {
            throw new BusinessException("票种不存在");
        }
        return type;
    }

    @Override
    public TicketAvailabilityVO ticketAvailability(Long ticketTypeId, LocalDate playDate) {
        TicketType type = ticketTypeId == null ? null : ticketTypeMapper.selectById(ticketTypeId);
        if (type == null) {
            throw new BusinessException("票种不存在");
        }
        LocalDate date = playDate == null ? LocalDate.now() : playDate;

        List<String> slots = TimeSlotUtil.parse(type.getTimeSlots());
        List<String> targets = slots.isEmpty() ? List.of(TimeSlotUtil.ALL_DAY) : slots;

        Map<String, TicketStock> bySlot = ticketStockMapper.selectList(Wrappers.<TicketStock>lambdaQuery()
                        .eq(TicketStock::getTicketTypeId, type.getId())
                        .eq(TicketStock::getStockDate, date)
                        .in(TicketStock::getTimeSlot, targets))
                .stream()
                .collect(Collectors.toMap(TicketStock::getTimeSlot, Function.identity(), (a, b) -> a));

        List<TicketAvailabilityVO.SlotStockVO> items = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            String slot = targets.get(i);
            TicketStock stock = bySlot.get(slot);
            // 该时段还没人订过（库存行未建）时，按票种默认库存推导，避免页面误显示 0 张
            int total = stock != null && stock.getTotalStock() != null
                    ? stock.getTotalStock()
                    : (slots.isEmpty()
                            ? (type.getStock() == null ? 0 : type.getStock())
                            : TimeSlotUtil.perSlotStock(type.getStock(), slots.size(), i));
            int remain = stock == null ? total : stock.remain();

            TicketAvailabilityVO.SlotStockVO item = new TicketAvailabilityVO.SlotStockVO();
            item.setTimeSlot(slot);
            item.setTotalStock(total);
            item.setRemainStock(remain);
            item.setAvailable(remain > 0 && (stock == null || stock.getStatus() == null || stock.getStatus() == 1));
            items.add(item);
        }

        TicketAvailabilityVO vo = new TicketAvailabilityVO();
        vo.setTicketTypeId(type.getId());
        vo.setTicketName(type.getTicketName());
        vo.setPlayDate(date);
        vo.setTimeSlotEnabled(!slots.isEmpty());
        vo.setSlots(items);
        return vo;
    }

    @Override
    public List<Announcement> listAnnouncements(Long scenicId, Integer limit) {
        Long target = resolveScenic(scenicId);
        int size = limit == null || limit <= 0 ? DEFAULT_ANNOUNCEMENT_LIMIT
                : Math.min(limit, MAX_ANNOUNCEMENT_LIMIT);
        return announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
                .and(w -> w.eq(Announcement::getScenicId, target).or().isNull(Announcement::getScenicId))
                .eq(Announcement::getStatus, ANNOUNCEMENT_STATUS_PUBLISHED)
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT " + size));
    }

    @Override
    public List<ParkingLot> listParking(Long scenicId) {
        Long target = resolveScenic(scenicId);
        return parkingLotMapper.selectList(Wrappers.<ParkingLot>lambdaQuery()
                .eq(ParkingLot::getScenicId, target)
                .orderByAsc(ParkingLot::getId));
    }

    @Override
    public List<ScenicSpot> mapSpots(Long scenicId) {
        Long target = resolveScenic(scenicId);
        // 地图导览必须要有坐标，缺经纬度的景点渲染不出来，直接过滤掉
        return scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                        .eq(ScenicSpot::getScenicId, target)
                        .isNotNull(ScenicSpot::getLongitude)
                        .isNotNull(ScenicSpot::getLatitude)
                        .orderByAsc(ScenicSpot::getId));
    }

    @Override
    public AiChatReplyVO chat(AiChatDTO dto) {
        // 未显式指定景区时，用游客注册/入场所属景区兜底，让知识库优先匹配本景区内容
        if (dto.getScenicId() == null) {
            dto.setScenicId(AppUserContext.getScenicId());
        }

        // AI 是外部依赖，慢或挂起会一直占住 HTTP 业务线程，并发上来足以拖垮整个接口层。
        // 这里放进独立的 aiExecutor 并设超时：最多占用 AI 专用线程，业务线程不会被占满。
        // AppUserContext 是 ThreadLocal，子线程取不到，需手动透传。
        Long touristId = AppUserContext.getTouristId();
        String touristNo = AppUserContext.getTouristNo();
        Long scenicId = AppUserContext.getScenicId();

        try {
            return aiExecutor.submit(() -> {
                AppUserContext.setTouristId(touristId);
                AppUserContext.setTouristNo(touristNo);
                AppUserContext.setScenicId(scenicId);
                try {
                    return aiService.chat(dto);
                } finally {
                    AppUserContext.clear();
                }
            }).get(ThreadPoolConfig.AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("AI 问答超时，scenicId={}", dto.getScenicId());
            throw new BusinessException("AI 服务响应超时，请稍后再试");
        } catch (RejectedExecutionException e) {
            log.warn("AI 问答被拒绝，线程池已满，scenicId={}", dto.getScenicId());
            throw new BusinessException("AI 服务繁忙，请稍后再试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("AI 服务调用被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            log.warn("AI 问答失败，scenicId={}，原因={}", dto.getScenicId(), cause == null ? null : cause.getMessage());
            // 业务校验异常原样透出，其余包装成统一提示，避免把堆栈抛给前端
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("AI 服务暂时不可用，请稍后再试");
        }
    }

    @Override
    public AppRouteVO recommendRoute(Long scenicId, String type) {
        Long target = resolveScenic(scenicId);
        List<ScenicSpot> openSpots = scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getScenicId, target)
                .eq(ScenicSpot::getStatus, SPOT_STATUS_OPEN));
        if (openSpots.isEmpty()) {
            throw new BusinessException("该景区暂无可游览景点");
        }

        String routeType = StringUtils.hasText(type) ? type.trim().toUpperCase() : "CLASSIC";
        List<ScenicSpot> picked;
        AppRouteVO vo = new AppRouteVO();
        switch (routeType) {
            case "FAMILY" -> {
                picked = openSpots.stream()
                        .filter(s -> "NATURAL".equals(s.getSpotType()) || "PLAY".equals(s.getSpotType()))
                        .sorted(Comparator.comparingInt(s -> nz(s.getSuggestDuration())))
                        .limit(6)
                        .collect(Collectors.toList());
                if (picked.isEmpty()) {
                    picked = openSpots.stream()
                            .sorted(Comparator.comparingInt(s -> nz(s.getSuggestDuration())))
                            .limit(6)
                            .collect(Collectors.toList());
                }
                vo.setRouteName("亲子轻松路线");
                vo.setDescription("精选自然与游乐类景点，节奏舒缓，适合带老人和儿童的家庭游玩。");
            }
            case "QUICK" -> {
                picked = openSpots.stream()
                        .sorted(Comparator.comparingInt(s -> nz(s.getSuggestDuration())))
                        .limit(4)
                        .collect(Collectors.toList());
                vo.setRouteName("半日精华路线");
                vo.setDescription("按游玩时长由短到长串联核心景点，半天即可游览精华。");
            }
            default -> {
                // 经典路线优先安排承载量大的主景点，先看最有代表性的景观
                picked = openSpots.stream()
                        .sorted(Comparator.comparingInt((ScenicSpot s) -> nz(s.getInstantCapacity())).reversed())
                        .limit(8)
                        .collect(Collectors.toList());
                vo.setRouteName("经典全览路线");
                vo.setDescription("覆盖景区主要景点，适合首次到访、希望完整体验的游客。");
            }
        }

        List<AppRouteSpotVO> spots = new ArrayList<>();
        int durationSum = 0;
        int crowded = 0;
        int order = 1;
        for (ScenicSpot spot : picked) {
            AppRouteSpotVO spotVO = new AppRouteSpotVO();
            spotVO.setOrder(order++);
            spotVO.setSpotId(spot.getId());
            spotVO.setSpotName(spot.getSpotName());
            spotVO.setSpotType(spot.getSpotType());
            spotVO.setSuggestDuration(spot.getSuggestDuration() == null
                    ? DEFAULT_SPOT_DURATION : spot.getSuggestDuration());
            spotVO.setDescription(spot.getDescription());
            spotVO.setCoverImg(spot.getCoverImg());
            spotVO.setLongitude(spot.getLongitude());
            spotVO.setLatitude(spot.getLatitude());
            spotVO.setCurrentCount(spot.getCurrentCount());
            spotVO.setStatus(spot.getStatus());
            spots.add(spotVO);

            durationSum += spotVO.getSuggestDuration();
            if (nz(spot.getInstantCapacity()) > 0
                    && nz(spot.getCurrentCount()) >= nz(spot.getInstantCapacity()) * DANGER_THRESHOLD / 100) {
                crowded++;
            }
        }

        vo.setSpots(spots);
        vo.setSpotCount(spots.size());
        // 加上景点之间的步行摆渡时间，给出的总时长才接近真实游览体验
        vo.setTotalDuration(durationSum + Math.max(spots.size() - 1, 0) * SPOT_WALK_MINUTES);
        vo.setTip(buildRouteTip(crowded, spots.size()));
        return vo;
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 解析首页/浏览接口实际使用的景区。
     *
     * <p>优先用请求参数；其次用游客 Token 中的注册景区；都没有时回落为第一个启用景区，
     * 保证小程序首次打开、未选景区时也能拿到内容，而不是直接报错。</p>
     */
    private Long resolveScenic(Long requested) {
        if (requested != null) {
            return requested;
        }
        Long fromToken = AppUserContext.getScenicId();
        if (fromToken != null) {
            return fromToken;
        }
        ScenicArea first = scenicAreaMapper.selectOne(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, SCENIC_STATUS_ENABLE)
                .orderByAsc(ScenicArea::getId)
                .last("LIMIT 1"));
        if (first == null) {
            throw new BusinessException("系统尚未配置可用景区");
        }
        return first.getId();
    }

    private void fillFlow(AppHomeVO vo, Long scenicId, Integer dailyCapacity) {
        PassengerFlow latest = passengerFlowMapper.selectOne(Wrappers.<PassengerFlow>lambdaQuery()
                .eq(PassengerFlow::getScenicId, scenicId)
                .eq(PassengerFlow::getStatDate, LocalDate.now())
                .orderByDesc(PassengerFlow::getStatHour)
                .last("LIMIT 1"));
        int currentCount = latest == null ? 0 : nz(latest.getCurrentCount());
        BigDecimal saturation = percentage(currentCount, nz(dailyCapacity));
        vo.setCurrentCount(currentCount);
        vo.setSaturationRate(saturation);
        String level = levelOf(saturation);
        vo.setFlowLevel(level);
        vo.setFlowTip(flowTip(level));
    }

    private BigDecimal percentage(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String levelOf(BigDecimal saturation) {
        if (saturation == null) {
            return "NORMAL";
        }
        double value = saturation.doubleValue();
        if (value >= DANGER_THRESHOLD) {
            return "DANGER";
        }
        return value >= WARNING_THRESHOLD ? "WARNING" : "NORMAL";
    }

    private String flowTip(String level) {
        return switch (level) {
            case "WARNING" -> "当前客流较多，建议错峰出行";
            case "DANGER" -> "当前客流已接近峰值，请谨慎前往";
            default -> "当前客流舒适，适合游览";
        };
    }

    private String buildRouteTip(int crowded, int total) {
        if (crowded == 0) {
            return "当前路线整体通畅，可按顺序依次游览。";
        }
        if (crowded == total) {
            return "路线上多数景点较为拥挤，建议错峰前往或适当调整顺序。";
        }
        return "路线上有 " + crowded + " 个景点较为拥挤，建议优先游览其他景点。";
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
