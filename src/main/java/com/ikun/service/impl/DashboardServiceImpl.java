package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.entity.AiLog;
import com.ikun.entity.Announcement;
import com.ikun.entity.Complaint;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.PassengerFlow;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.Tourist;
import com.ikun.mapper.*;
import com.ikun.service.DashboardService;
import com.ikun.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 数据概览服务实现
 *
 * <p>统计口径说明：
 * <ul>
 *     <li>趋势类数据统一取"近 7 天"（含今天），数组长度固定为 7，最后一项为今天；</li>
 *     <li>入园 / 在园 / 小时分布来自 passenger_flow，营收类仅统计已支付、已核销订单；</li>
 *     <li>传了 scenicId 时按景区过滤，未传时统计全部启用景区。</li>
 * </ul>
 *
 * @author smart-scenic
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 趋势天数（含今天） */
    private static final int TREND_DAYS = 7;

    /** 统一离园时刻：当日 18:00 后实时在园归零（与客流概览/订单核销侧同一口径） */
    private static final int LEAVE_HOUR = 18;

    /** 计入营收的订单状态 */
    private static final List<String> REVENUE_STATUS = List.of("PAID", "VERIFIED");

    /** 首页公告展示条数 */
    private static final int ANNOUNCEMENT_LIMIT = 6;

    /** 趋势图 X 轴日期格式：M/d，随数据一起下发，避免前端按本地时区另算导致错位 */
    private static final DateTimeFormatter TREND_DATE_FMT = DateTimeFormatter.ofPattern("M/d");

    private final ScenicAreaMapper scenicAreaMapper;
    private final TouristMapper touristMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final ComplaintMapper complaintMapper;
    private final FlowWarningMapper flowWarningMapper;
    private final PassengerFlowMapper passengerFlowMapper;
    private final AiLogMapper aiLogMapper;
    private final AnnouncementMapper announcementMapper;

    @Override
    public DashboardVO stats(Long scenicId) {
        DashboardVO vo = new DashboardVO();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(TREND_DAYS - 1L);
        LocalDateTime weekStart = start.atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();

        // ===================== 景区 / 游客 =====================
        vo.setScenicCount(scenicAreaMapper.selectCount(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, "ENABLE")));

        Long touristTotal = touristMapper.selectCount(null);
        vo.setTouristCount(touristTotal);

        vo.setTodayNewTouristCount(touristMapper.selectCount(Wrappers.<Tourist>lambdaQuery()
                .ge(Tourist::getCreateTime, todayStart)
                .eq(scenicId != null, Tourist::getRegisterScenicId, scenicId)));

        Long blacklistTotal = touristMapper.selectCount(Wrappers.<Tourist>lambdaQuery()
                .eq(Tourist::getIsBlacklist, 1)
                .eq(scenicId != null, Tourist::getRegisterScenicId, scenicId));
        vo.setBlacklistRate(rate(blacklistTotal, touristTotal));

        // ===================== 客流：近 7 日趋势 + 今日 24 小时分布 =====================
        List<PassengerFlow> weekFlows = passengerFlowMapper.selectList(Wrappers.<PassengerFlow>lambdaQuery()
                .ge(PassengerFlow::getStatDate, start)
                .le(PassengerFlow::getStatDate, today)
                .eq(scenicId != null, PassengerFlow::getScenicId, scenicId));

        Map<LocalDate, Long> enterByDay = weekFlows.stream()
                .filter(f -> f.getStatDate() != null && f.getEnterCount() != null)
                .collect(Collectors.groupingBy(PassengerFlow::getStatDate,
                        Collectors.summingLong(PassengerFlow::getEnterCount)));

        Map<LocalDate, Integer> onlinePeakByDay = new HashMap<>();
        weekFlows.forEach(f -> {
            if (f.getStatDate() == null || f.getCurrentCount() == null) {
                return;
            }
            onlinePeakByDay.merge(f.getStatDate(), f.getCurrentCount(), Math::max);
        });

        List<Long> enterTrend = new ArrayList<>(TREND_DAYS);
        List<Long> onlineTrend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            enterTrend.add(enterByDay.getOrDefault(day, 0L));
            onlineTrend.add(onlinePeakByDay.getOrDefault(day, 0).longValue());
        }
        vo.setTodayEnterTrend(enterTrend);
        vo.setOnlineTrend(onlineTrend);

        // 今日 24 小时入园分布
        List<Integer> hourly = new ArrayList<>(24);
        for (int i = 0; i < 24; i++) {
            hourly.add(0);
        }
        weekFlows.stream()
                .filter(f -> today.equals(f.getStatDate()))
                .filter(f -> f.getStatHour() != null && f.getEnterCount() != null)
                .filter(f -> f.getStatHour() >= 0 && f.getStatHour() < 24)
                .forEach(f -> hourly.set(f.getStatHour(), hourly.get(f.getStatHour()) + f.getEnterCount()));
        vo.setHourlyDistribution(hourly);

        long todayEnter = enterByDay.getOrDefault(today, 0L);
        long onlineNow = onlinePeakByDay.getOrDefault(today, 0).longValue();
        // 业务闭环：当日 18:00 统一离园，之后实时在园归零；
        // 趋势曲线保留当日峰值（历史事实），仅实时数字清零
        if (LocalTime.now().getHour() >= LEAVE_HOUR) {
            onlineNow = 0;
        }
        vo.setTodayEnterCount(todayEnter);
        vo.setCurrentOnlineCount(onlineNow);
        vo.setOnlineCount(onlineNow);

        // 日承载量：单景区取景区承载量，全部景区取启用景区之和
        if (scenicId != null) {
            ScenicArea area = scenicAreaMapper.selectById(scenicId);
            vo.setCapacity(area != null && area.getDailyCapacity() != null
                    ? area.getDailyCapacity().longValue() : 0L);
        } else {
            List<ScenicArea> areas = scenicAreaMapper.selectList(Wrappers.<ScenicArea>lambdaQuery()
                    .eq(ScenicArea::getStatus, "ENABLE"));
            vo.setCapacity(areas.stream()
                    .map(ScenicArea::getDailyCapacity)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum());
        }

        // ===================== 订单 / 营收：近 7 日趋势 =====================
        // 口径说明：营收是「钱什么时候到账」，必须按支付时间 pay_time 归集。
        // 早期实现按游玩日期 play_date 统计，游客今天买三天后的票会出现
        // 「今天已收款、趋势图上却是空的，三天后才冒出数据」的错位，近 7 天几乎必然为空。
        LocalDateTime weekEndExclusive = today.plusDays(1).atStartOfDay();

        // 成交订单：支付时间落在窗口内，且状态为已支付 / 已核销（与营收同一口径）
        List<TicketOrder> weekPaidOrders = ticketOrderMapper.selectList(Wrappers.<TicketOrder>lambdaQuery()
                .ge(TicketOrder::getPayTime, weekStart)
                .lt(TicketOrder::getPayTime, weekEndExclusive)
                .in(TicketOrder::getStatus, REVENUE_STATUS)
                .eq(scenicId != null, TicketOrder::getScenicId, scenicId));

        Map<LocalDate, Long> paidOrderByDay = weekPaidOrders.stream()
                .filter(o -> o.getPayTime() != null)
                .collect(Collectors.groupingBy(o -> o.getPayTime().toLocalDate(), Collectors.counting()));

        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        weekPaidOrders.stream()
                .filter(o -> o.getPayTime() != null && o.getTotalAmount() != null)
                .forEach(o -> revenueByDay.merge(o.getPayTime().toLocalDate(),
                        o.getTotalAmount(), BigDecimal::add));

        List<String> trendDates = new ArrayList<>(TREND_DAYS);
        List<Long> orderTrend = new ArrayList<>(TREND_DAYS);
        List<Long> revenueTrend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            trendDates.add(day.format(TREND_DATE_FMT));
            orderTrend.add(paidOrderByDay.getOrDefault(day, 0L));
            revenueTrend.add(revenueByDay.getOrDefault(day, BigDecimal.ZERO)
                    .setScale(0, RoundingMode.HALF_UP).longValue());
        }
        vo.setTrendDates(trendDates);
        vo.setOrderTrend(orderTrend);
        vo.setRevenueTrend(revenueTrend);

        // 今日订单数按下单时间（含待支付），今日营收按支付时间（仅已支付 / 已核销）
        Long todayOrderCount = ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .ge(TicketOrder::getCreateTime, todayStart)
                .lt(TicketOrder::getCreateTime, weekEndExclusive)
                .eq(scenicId != null, TicketOrder::getScenicId, scenicId));
        vo.setTodayOrderCount(todayOrderCount == null ? 0L : todayOrderCount);
        vo.setTodayOrderAmount(revenueByDay.getOrDefault(today, BigDecimal.ZERO));

        // 订单转化率：近 7 日成交订单数 / 近 7 日入园人次
        long paidOrders = weekPaidOrders.size();
        long weekEnter = enterTrend.stream().mapToLong(Long::longValue).sum();
        vo.setOrderConversion(rate(paidOrders, weekEnter));

        // 营收日环比（今日 vs 昨日，百分比）
        BigDecimal todayRevenue = revenueByDay.getOrDefault(today, BigDecimal.ZERO);
        BigDecimal yesterdayRevenue = revenueByDay.getOrDefault(today.minusDays(1), BigDecimal.ZERO);
        if (yesterdayRevenue.signum() == 0) {
            vo.setRevenueDelta(todayRevenue.signum() == 0
                    ? BigDecimal.ZERO : BigDecimal.valueOf(100));
        } else {
            vo.setRevenueDelta(todayRevenue.subtract(yesterdayRevenue)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(yesterdayRevenue, 2, RoundingMode.HALF_UP));
        }

        // ===================== 工单 / 预警 =====================
        vo.setPendingComplaintCount(complaintMapper.selectCount(Wrappers.<Complaint>lambdaQuery()
                .eq(Complaint::getStatus, "PENDING")
                .eq(scenicId != null, Complaint::getScenicId, scenicId)));

        vo.setUnhandledWarningCount(flowWarningMapper.selectCount(Wrappers.<FlowWarning>lambdaQuery()
                .eq(FlowWarning::getStatus, "UNHANDLED")
                .eq(scenicId != null, FlowWarning::getScenicId, scenicId)));

        List<Complaint> weekComplaints = complaintMapper.selectList(Wrappers.<Complaint>lambdaQuery()
                .ge(Complaint::getCreateTime, weekStart)
                .eq(scenicId != null, Complaint::getScenicId, scenicId));
        Map<LocalDate, Long> complaintByDay = weekComplaints.stream()
                .filter(c -> c.getCreateTime() != null)
                .collect(Collectors.groupingBy(c -> c.getCreateTime().toLocalDate(), Collectors.counting()));
        List<Long> complaintTrend = new ArrayList<>(TREND_DAYS);
        for (int i = 0; i < TREND_DAYS; i++) {
            complaintTrend.add(complaintByDay.getOrDefault(start.plusDays(i), 0L));
        }
        vo.setComplaintTrend(complaintTrend);

        // ===================== AI 响应成功率（近 7 天） =====================
        Long aiTotal = aiLogMapper.selectCount(Wrappers.<AiLog>lambdaQuery()
                .eq(scenicId != null, AiLog::getScenicId, scenicId)
                .ge(AiLog::getCreateTime, weekStart));
        vo.setAiResponseRate(aiTotal == 0 ? BigDecimal.ZERO
                : rate(aiLogMapper.selectCount(Wrappers.<AiLog>lambdaQuery()
                        .eq(scenicId != null, AiLog::getScenicId, scenicId)
                        .eq(AiLog::getStatus, "SUCCESS")
                        .ge(AiLog::getCreateTime, weekStart)), aiTotal));

        // ===================== 最新公告 =====================
        vo.setAnnouncementList(announcementMapper.selectList(Wrappers.<Announcement>lambdaQuery()
                .eq(Announcement::getStatus, "PUBLISHED")
                .and(scenicId != null, w -> w.eq(Announcement::getScenicId, scenicId)
                        .or().isNull(Announcement::getScenicId))
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getPublishTime)
                .last("LIMIT " + ANNOUNCEMENT_LIMIT)));

        return vo;
    }

    /**
     * 安全占比计算，分母为 0 或 null 时返回 0
     */
    private BigDecimal rate(long numerator, Long denominator) {
        if (denominator == null || denominator == 0L) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
        // 比率统一收敛到 [0,1]：客流等基础数据缺失时，分子可能大于分母（例如
        // 近 7 日只录到 1 次入园却有 4 笔成交订单），直接输出会出现「转化率 400%」
        // 这种超过 100% 的不可读数值。
        if (v.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return v.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : v;
    }
}
