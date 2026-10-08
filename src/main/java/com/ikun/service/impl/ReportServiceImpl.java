package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.BusinessException;
import com.ikun.common.ScenicScope;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.TicketOrder;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.service.ReportService;
import com.ikun.vo.RevenueReportV2VO;
import com.ikun.vo.StatItemVO;
import com.ikun.vo.TicketReportVO;
import com.ikun.vo.TicketSaleItemVO;
import com.ikun.vo.TrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据报表服务实现
 *
 * <p><b>金额口径统一说明</b>：本项目把所有营收类指标统一为
 * 「毛营收 = 已支付 + 已核销订单金额」「退款金额 = 已退款订单金额」
 * 「净营收 = 毛营收 - 退款金额」，退款处理中的订单两边都不计。
 * 口径写死在实现里并由注释固定下来，避免不同报表各算各的、月底对不上账。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REFUNDED = "REFUNDED";

    /** 统计默认时间窗：近 30 天 */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final TicketOrderMapper ticketOrderMapper;
    private final ScenicAreaMapper scenicAreaMapper;

    @Override
    public TicketReportVO ticketReport(Long requestedScenicId, LocalDate startDate, LocalDate endDate) {
        Long scenicId = ScenicScope.resolve(requestedScenicId);
        DateRange range = resolveRange(startDate, endDate);
        List<TicketOrder> orders = queryOrders(scenicId, range);

        // 按票种分组累加：一次遍历同时算出销售、核销、退款三个维度
        Map<Long, TicketSaleItemVO> itemMap = new LinkedHashMap<>();
        Map<String, Long> dailyQuantity = new LinkedHashMap<>();
        Map<String, BigDecimal> dailyAmount = new LinkedHashMap<>();
        int totalQuantity = 0;
        int verifiedQuantity = 0;
        int refundedQuantity = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal refundedAmount = BigDecimal.ZERO;

        for (TicketOrder order : orders) {
            int quantity = order.getQuantity() == null ? 0 : order.getQuantity();
            BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            TicketSaleItemVO item = itemMap.computeIfAbsent(order.getTicketTypeId(), key -> {
                TicketSaleItemVO vo = new TicketSaleItemVO();
                vo.setTicketTypeId(key);
                vo.setTicketName(order.getTicketName());
                vo.setScenicName(resolveScenicName(order.getScenicId()));
                vo.setQuantity(0);
                vo.setVerifiedQuantity(0);
                vo.setRefundedQuantity(0);
                vo.setAmount(BigDecimal.ZERO);
                vo.setRefundedAmount(BigDecimal.ZERO);
                return vo;
            });

            if (STATUS_PAID.equals(order.getStatus()) || STATUS_VERIFIED.equals(order.getStatus())) {
                item.setQuantity(item.getQuantity() + quantity);
                item.setAmount(item.getAmount().add(amount));
                totalQuantity += quantity;
                totalAmount = totalAmount.add(amount);
                if (STATUS_VERIFIED.equals(order.getStatus())) {
                    item.setVerifiedQuantity(item.getVerifiedQuantity() + quantity);
                    verifiedQuantity += quantity;
                }
                if (order.getCreateTime() != null) {
                    String day = order.getCreateTime().toLocalDate().format(DATE_FORMAT);
                    dailyQuantity.merge(day, (long) quantity, Long::sum);
                    dailyAmount.merge(day, amount, BigDecimal::add);
                }
            } else if (STATUS_REFUNDED.equals(order.getStatus())) {
                item.setRefundedQuantity(item.getRefundedQuantity() + quantity);
                item.setRefundedAmount(item.getRefundedAmount().add(amount));
                refundedQuantity += quantity;
                refundedAmount = refundedAmount.add(amount);
            }
        }

        List<TicketSaleItemVO> items = new ArrayList<>(itemMap.values());
        items.forEach(item -> item.setVerifiedRate(rate(item.getVerifiedQuantity(), item.getQuantity())));
        items.sort(Comparator.comparing(TicketSaleItemVO::getAmount,
                Comparator.nullsLast(Comparator.reverseOrder())));

        TicketReportVO report = new TicketReportVO();
        report.setScenicId(scenicId);
        report.setScenicName(scenicId == null ? "全部景区" : resolveScenicName(scenicId));
        report.setStartDate(range.start());
        report.setEndDate(range.end());
        report.setTotalQuantity(totalQuantity);
        report.setTotalAmount(totalAmount.setScale(2, RoundingMode.HALF_UP));
        report.setVerifiedQuantity(verifiedQuantity);
        report.setVerifiedRate(rate(verifiedQuantity, totalQuantity));
        report.setRefundedQuantity(refundedQuantity);
        report.setRefundedAmount(refundedAmount.setScale(2, RoundingMode.HALF_UP));
        report.setItems(items);
        report.setQuantityTrend(buildCountTrend(dailyQuantity, range));
        report.setAmountTrend(buildAmountTrend(dailyAmount, range));
        return report;
    }

    @Override
    public RevenueReportV2VO revenueReport(Long requestedScenicId, LocalDate startDate, LocalDate endDate) {
        Long scenicId = ScenicScope.resolve(requestedScenicId);
        DateRange range = resolveRange(startDate, endDate);
        List<TicketOrder> orders = queryOrders(scenicId, range);

        Map<String, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        Map<String, Long> dailyOrders = new LinkedHashMap<>();
        Map<String, BigDecimal> channelRevenue = new LinkedHashMap<>();
        Map<Long, BigDecimal> scenicRevenue = new LinkedHashMap<>();
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal refundAmount = BigDecimal.ZERO;
        int validOrderCount = 0;

        for (TicketOrder order : orders) {
            BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            if (order.getCreateTime() != null) {
                String day = order.getCreateTime().toLocalDate().format(DATE_FORMAT);
                dailyOrders.merge(day, 1L, Long::sum);
            }
            if (STATUS_PAID.equals(order.getStatus()) || STATUS_VERIFIED.equals(order.getStatus())) {
                grossRevenue = grossRevenue.add(amount);
                validOrderCount++;
                if (order.getCreateTime() != null) {
                    dailyRevenue.merge(order.getCreateTime().toLocalDate().format(DATE_FORMAT),
                            amount, BigDecimal::add);
                }
                channelRevenue.merge(channelText(order.getChannel()), amount, BigDecimal::add);
                if (order.getScenicId() != null) {
                    scenicRevenue.merge(order.getScenicId(), amount, BigDecimal::add);
                }
            } else if (STATUS_REFUNDED.equals(order.getStatus())) {
                refundAmount = refundAmount.add(amount);
            }
        }

        RevenueReportV2VO report = new RevenueReportV2VO();
        report.setScenicId(scenicId);
        report.setScenicName(scenicId == null ? "全部景区" : resolveScenicName(scenicId));
        report.setStartDate(range.start());
        report.setEndDate(range.end());
        report.setTotalOrders((long) orders.size());
        report.setGrossRevenue(grossRevenue.setScale(2, RoundingMode.HALF_UP));
        report.setRefundAmount(refundAmount.setScale(2, RoundingMode.HALF_UP));
        report.setNetRevenue(grossRevenue.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP));
        report.setAvgOrderAmount(validOrderCount == 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : grossRevenue.divide(BigDecimal.valueOf(validOrderCount), 2, RoundingMode.HALF_UP));
        report.setRevenueTrend(buildAmountTrend(dailyRevenue, range));
        report.setOrderTrend(buildCountTrend(dailyOrders, range));

        List<StatItemVO> channelItems = new ArrayList<>();
        channelRevenue.forEach((name, value) ->
                channelItems.add(new StatItemVO(name, value.setScale(2, RoundingMode.HALF_UP).longValue())));
        channelItems.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        report.setChannelDist(channelItems);

        Map<Long, String> scenicNames = scenicNameIndex(scenicRevenue.keySet());
        List<StatItemVO> scenicItems = new ArrayList<>();
        scenicRevenue.forEach((id, value) -> scenicItems.add(new StatItemVO(
                scenicNames.getOrDefault(id, "未知景区"),
                value.setScale(2, RoundingMode.HALF_UP).longValue())));
        scenicItems.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        report.setScenicDist(scenicItems);
        return report;
    }

    /* ==================== 私有方法 ==================== */

    private List<TicketOrder> queryOrders(Long scenicId, DateRange range) {
        return ticketOrderMapper.selectList(Wrappers.<TicketOrder>lambdaQuery()
                .eq(scenicId != null, TicketOrder::getScenicId, scenicId)
                .ge(TicketOrder::getCreateTime, range.start().atStartOfDay())
                .le(TicketOrder::getCreateTime, LocalDateTime.of(range.end(), LocalTime.MAX)));
    }

    private DateRange resolveRange(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        return new DateRange(start, end);
    }

    private Map<Long, String> scenicNameIndex(Set<Long> scenicIds) {
        if (scenicIds == null || scenicIds.isEmpty()) {
            return Map.of();
        }
        return scenicAreaMapper.selectBatchIds(scenicIds).stream()
                .collect(Collectors.toMap(ScenicArea::getId, ScenicArea::getScenicName, (a, b) -> a));
    }

    private String resolveScenicName(Long scenicId) {
        if (scenicId == null) {
            return "全部景区";
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        return area == null ? "未知景区" : area.getScenicName();
    }

    private List<TrendItemVO> buildCountTrend(Map<String, Long> dailyCount, DateRange range) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyCount.getOrDefault(key, 0L)));
        }
        return trend;
    }

    private List<TrendItemVO> buildAmountTrend(Map<String, BigDecimal> dailyAmount, DateRange range) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyAmount.getOrDefault(key, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)));
        }
        return trend;
    }

    private BigDecimal rate(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private String channelText(String channel) {
        if (!StringUtils.hasText(channel)) {
            return "未知渠道";
        }
        return switch (channel) {
            case "MINI_PROGRAM" -> "小程序";
            case "OTA" -> "OTA平台";
            case "WINDOW" -> "售票窗口";
            case "AGENCY" -> "旅行社";
            default -> channel;
        };
    }

    /** 内部使用的闭区间日期范围 */
    private record DateRange(LocalDate start, LocalDate end) {
    }
}
