package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.dto.FlowWarningHandleDTO;
import com.ikun.entity.CheckinRecord;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.PassengerFlow;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.ScenicSpot;
import com.ikun.mapper.CheckinRecordMapper;
import com.ikun.mapper.FlowWarningMapper;
import com.ikun.mapper.PassengerFlowMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.ScenicSpotMapper;
import com.ikun.service.PassengerFlowService;
import com.ikun.vo.FlowOverviewVO;
import com.ikun.vo.FlowReportVO;
import com.ikun.vo.SpotFlowVO;
import com.ikun.vo.StatItemVO;
import com.ikun.vo.TrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客流统计服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PassengerFlowServiceImpl implements PassengerFlowService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 饱和度阈值：达到 70% 预警，达到 90% 视为超载 */
    private static final double WARNING_THRESHOLD = 70D;
    private static final double DANGER_THRESHOLD = 90D;

    private static final String LEVEL_NORMAL = "NORMAL";
    private static final String LEVEL_WARNING = "WARNING";
    private static final String LEVEL_DANGER = "DANGER";

    private final PassengerFlowMapper passengerFlowMapper;
    private final FlowWarningMapper flowWarningMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final ScenicSpotMapper scenicSpotMapper;

    @Override
    public FlowOverviewVO overview(Long requestedScenicId, LocalDate requestedDate) {
        Long scenicId = resolveRequiredScenic(requestedScenicId);
        LocalDate date = requestedDate != null ? requestedDate : LocalDate.now();
        ScenicArea area = scenicAreaMapper.selectById(scenicId);

        List<PassengerFlow> flows = passengerFlowMapper.selectList(Wrappers.<PassengerFlow>lambdaQuery()
                .eq(PassengerFlow::getScenicId, scenicId)
                .eq(PassengerFlow::getStatDate, date)
                .orderByAsc(PassengerFlow::getStatHour));

        int enterTotal = 0;
        int leaveTotal = 0;
        int currentCount = 0;
        Map<Integer, Integer> hourlyEnter = new LinkedHashMap<>();
        Map<Integer, Integer> hourlyOnline = new LinkedHashMap<>();
        for (PassengerFlow flow : flows) {
            enterTotal += nz(flow.getEnterCount());
            leaveTotal += nz(flow.getLeaveCount());
            if (flow.getStatHour() != null) {
                hourlyEnter.merge(flow.getStatHour(), nz(flow.getEnterCount()), Integer::sum);
                // 在园人数是瞬时值，按小时保留即可，供趋势图「在园」曲线使用
                hourlyOnline.put(flow.getStatHour(), nz(flow.getCurrentCount()));
            }
            // 在园人数是瞬时值，取最后一个已知时刻的数据即为「当前」
            if (flow.getCurrentCount() != null) {
                currentCount = flow.getCurrentCount();
            }
        }

        int capacity = area != null && area.getDailyCapacity() != null ? area.getDailyCapacity() : 0;
        BigDecimal saturation = percentage(currentCount, capacity);

        FlowOverviewVO vo = new FlowOverviewVO();
        vo.setScenicId(scenicId);
        vo.setScenicName(area == null ? null : area.getScenicName());
        vo.setStatDate(date);
        vo.setCurrentCount(currentCount);
        vo.setTodayEnterCount(enterTotal);
        vo.setTodayLeaveCount(leaveTotal);
        vo.setCapacity(capacity);
        vo.setSaturationRate(saturation);
        vo.setWarningLevel(levelOf(saturation));

        // 各景点实时明细：承载量为 0 的景点（如免费开放区域）不算异常，否则会一直飘红
        List<ScenicSpot> spots = scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(ScenicSpot::getScenicId, scenicId)
                .orderByDesc(ScenicSpot::getCurrentCount));
        List<SpotFlowVO> spotFlows = new ArrayList<>();
        int warnSpotCount = 0;
        for (ScenicSpot spot : spots) {
            SpotFlowVO spotVO = new SpotFlowVO();
            spotVO.setSpotId(spot.getId());
            spotVO.setSpotName(spot.getSpotName());
            spotVO.setSpotType(spot.getSpotType());
            spotVO.setCurrentCount(nz(spot.getCurrentCount()));
            spotVO.setInstantCapacity(spot.getInstantCapacity());
            spotVO.setStatus(spot.getStatus());
            BigDecimal spotSaturation = percentage(nz(spot.getCurrentCount()), nz(spot.getInstantCapacity()));
            spotVO.setSaturationRate(spotSaturation);
            spotVO.setWarningLevel(levelOf(spotSaturation));
            if (LEVEL_WARNING.equals(spotVO.getWarningLevel()) || LEVEL_DANGER.equals(spotVO.getWarningLevel())) {
                warnSpotCount++;
            }
            spotFlows.add(spotVO);
        }
        vo.setSpotFlows(spotFlows);
        vo.setWarnSpotCount(warnSpotCount);

        List<TrendItemVO> hourlyTrend = new ArrayList<>();
        List<TrendItemVO> onlineTrend = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            String label = String.format("%02d:00", hour);
            hourlyTrend.add(new TrendItemVO(label, (long) hourlyEnter.getOrDefault(hour, 0)));
            onlineTrend.add(new TrendItemVO(label, (long) hourlyOnline.getOrDefault(hour, 0)));
        }
        vo.setHourlyTrend(hourlyTrend);
        vo.setOnlineTrend(onlineTrend);
        return vo;
    }

    @Override
    public PageResult<PassengerFlow> pageQuery(Integer pageNum, Integer pageSize, Long scenicId,
                                               LocalDate startDate, LocalDate endDate) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<PassengerFlow>lambdaQuery()
                .eq(scopeScenicId != null, PassengerFlow::getScenicId, scopeScenicId)
                .ge(startDate != null, PassengerFlow::getStatDate, startDate)
                .le(endDate != null, PassengerFlow::getStatDate, endDate)
                .orderByDesc(PassengerFlow::getStatDate)
                .orderByAsc(PassengerFlow::getStatHour);
        return PageResult.of(passengerFlowMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public PageResult<CheckinRecord> pageCheckin(Integer pageNum, Integer pageSize, Long scenicId,
                                                 String status, String verifyType, String orderNo,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<CheckinRecord>lambdaQuery()
                .eq(scopeScenicId != null, CheckinRecord::getScenicId, scopeScenicId)
                .eq(StringUtils.hasText(status), CheckinRecord::getStatus, status)
                .eq(StringUtils.hasText(verifyType), CheckinRecord::getVerifyType, verifyType)
                .like(StringUtils.hasText(orderNo), CheckinRecord::getOrderNo, orderNo)
                .ge(startTime != null, CheckinRecord::getVerifyTime, startTime)
                .le(endTime != null, CheckinRecord::getVerifyTime, endTime)
                .orderByDesc(CheckinRecord::getVerifyTime);
        return PageResult.of(checkinRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public PageResult<FlowWarning> pageWarning(Integer pageNum, Integer pageSize, Long scenicId,
                                               String status, String warningLevel) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<FlowWarning>lambdaQuery()
                .eq(scopeScenicId != null, FlowWarning::getScenicId, scopeScenicId)
                .eq(StringUtils.hasText(status), FlowWarning::getStatus, status)
                .eq(StringUtils.hasText(warningLevel), FlowWarning::getWarningLevel, warningLevel)
                .orderByDesc(FlowWarning::getCreateTime);
        return PageResult.of(flowWarningMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleWarning(FlowWarningHandleDTO dto) {
        FlowWarning warning = flowWarningMapper.selectById(dto.getWarningId());
        if (warning == null) {
            throw new BusinessException("预警记录不存在");
        }
        ScenicScope.checkWritable(warning.getScenicId());
        if ("HANDLED".equals(warning.getStatus())) {
            throw new BusinessException("该预警已处理，无需重复处置");
        }
        FlowWarning update = new FlowWarning();
        update.setId(dto.getWarningId());
        update.setStatus("HANDLED");
        update.setHandleRemark(dto.getHandleRemark());
        update.setHandlerId(UserContext.getUserId());
        update.setHandleTime(LocalDateTime.now());
        flowWarningMapper.updateById(update);
        log.info("客流预警已处理：warningId={}，handlerId={}", dto.getWarningId(), UserContext.getUserId());
    }

    @Override
    public FlowReportVO report(Long requestedScenicId, LocalDate startDate, LocalDate endDate) {
        Long scenicId = resolveRequiredScenic(requestedScenicId);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);

        List<PassengerFlow> flows = passengerFlowMapper.selectList(Wrappers.<PassengerFlow>lambdaQuery()
                .eq(PassengerFlow::getScenicId, scenicId)
                .ge(PassengerFlow::getStatDate, start)
                .le(PassengerFlow::getStatDate, end));

        Map<String, Long> dailyEnter = new LinkedHashMap<>();
        Map<String, Long> dailyLeave = new LinkedHashMap<>();
        Map<String, Long> levelCount = new LinkedHashMap<>();
        long totalEnter = 0;
        long totalLeave = 0;
        int maxCurrent = 0;
        for (PassengerFlow flow : flows) {
            String day = flow.getStatDate() == null ? null : flow.getStatDate().format(DATE_FORMAT);
            totalEnter += nz(flow.getEnterCount());
            totalLeave += nz(flow.getLeaveCount());
            if (day != null) {
                dailyEnter.merge(day, (long) nz(flow.getEnterCount()), Long::sum);
                dailyLeave.merge(day, (long) nz(flow.getLeaveCount()), Long::sum);
            }
            if (flow.getCurrentCount() != null && flow.getCurrentCount() > maxCurrent) {
                maxCurrent = flow.getCurrentCount();
            }
            levelCount.merge(levelText(flow.getWarningLevel()), 1L, Long::sum);
        }

        // 单日峰值：逐日比对，同时记下峰值日期，报表里「峰值出现在哪天」比峰值本身更有意义
        int peakEnter = 0;
        String peakDate = null;
        for (Map.Entry<String, Long> entry : dailyEnter.entrySet()) {
            if (entry.getValue() > peakEnter) {
                peakEnter = entry.getValue().intValue();
                peakDate = entry.getKey();
            }
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        BigDecimal avgEnter = BigDecimal.valueOf(totalEnter)
                .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        int capacity = area != null && area.getDailyCapacity() != null ? area.getDailyCapacity() : 0;

        // 预警统计以 flow_warning 表为准：它记录了实际触发过的预警，
        // 而 passenger_flow 里只是每个时段的瞬时级别，两者口径不同
        Long warningCount = flowWarningMapper.selectCount(Wrappers.<FlowWarning>lambdaQuery()
                .eq(FlowWarning::getScenicId, scenicId)
                .eq(FlowWarning::getWarningLevel, LEVEL_WARNING)
                .ge(FlowWarning::getCreateTime, start.atStartOfDay())
                .le(FlowWarning::getCreateTime, LocalDateTime.of(end, LocalTime.MAX)));
        Long dangerCount = flowWarningMapper.selectCount(Wrappers.<FlowWarning>lambdaQuery()
                .eq(FlowWarning::getScenicId, scenicId)
                .eq(FlowWarning::getWarningLevel, LEVEL_DANGER)
                .ge(FlowWarning::getCreateTime, start.atStartOfDay())
                .le(FlowWarning::getCreateTime, LocalDateTime.of(end, LocalTime.MAX)));

        FlowReportVO report = new FlowReportVO();
        report.setScenicId(scenicId);
        report.setScenicName(area == null ? null : area.getScenicName());
        report.setStartDate(start);
        report.setEndDate(end);
        report.setTotalEnterCount(totalEnter);
        report.setTotalLeaveCount(totalLeave);
        report.setAvgEnterCount(avgEnter);
        report.setPeakEnterCount(peakEnter);
        report.setPeakDate(peakDate);
        report.setMaxCurrentCount(maxCurrent);
        report.setCapacityUtilization(percentage(avgEnter.intValue(), capacity));
        report.setWarningCount(warningCount == null ? 0L : warningCount);
        report.setDangerCount(dangerCount == null ? 0L : dangerCount);
        report.setEnterTrend(buildDailyTrend(dailyEnter, start, end));
        report.setLeaveTrend(buildDailyTrend(dailyLeave, start, end));

        List<StatItemVO> levelItems = new ArrayList<>();
        levelCount.forEach((name, value) -> levelItems.add(new StatItemVO(name, value)));
        report.setWarningLevelDist(levelItems);
        return report;
    }

    /* ==================== 私有方法 ==================== */

    /**
     * 解析出必定非空的景区ID
     *
     * <p>超管未指定景区时回落为「第一个启用景区」，而不是抛错或返回全平台汇总——
     * 概览页的所有指标都是按单景区容量计算的，混在一起算出来的饱和度没有意义。</p>
     */
    private Long resolveRequiredScenic(Long requested) {
        Long scenicId = ScenicScope.resolve(requested);
        if (scenicId != null) {
            return scenicId;
        }
        ScenicArea first = scenicAreaMapper.selectOne(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, "ENABLE")
                .orderByAsc(ScenicArea::getId)
                .last("LIMIT 1"));
        if (first == null) {
            throw new BusinessException("系统尚未配置可用景区，无法统计客流");
        }
        return first.getId();
    }

    private List<TrendItemVO> buildDailyTrend(Map<String, Long> dailyCount, LocalDate start, LocalDate end) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyCount.getOrDefault(key, 0L)));
        }
        return trend;
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
            return LEVEL_NORMAL;
        }
        double value = saturation.doubleValue();
        if (value >= DANGER_THRESHOLD) {
            return LEVEL_DANGER;
        }
        return value >= WARNING_THRESHOLD ? LEVEL_WARNING : LEVEL_NORMAL;
    }

    private String levelText(String level) {
        if (level == null) {
            return "正常";
        }
        return switch (level) {
            case LEVEL_WARNING -> "预警";
            case LEVEL_DANGER -> "超载";
            default -> "正常";
        };
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
