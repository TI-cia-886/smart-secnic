package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.dto.RefundAuditDTO;
import com.ikun.entity.CheckinRecord;
import com.ikun.entity.PassengerFlow;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.SysUser;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.Tourist;
import com.ikun.mapper.CheckinRecordMapper;
import com.ikun.mapper.PassengerFlowMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.SysUserMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TicketStockMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.TicketOrderService;
import com.ikun.util.SensitiveUtil;
import com.ikun.util.TimeSlotUtil;
import com.ikun.vo.OrderStatVO;
import com.ikun.vo.StatItemVO;
import com.ikun.vo.TicketOrderVO;
import com.ikun.vo.TrendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单预约服务实现
 *
 * <p>订单状态机：{@code PENDING_PAY -> PAID -> VERIFIED}，
 * 以及 {@code PAID -> REFUNDING -> (REFUNDED | PAID)}、{@code PENDING_PAY -> CANCELLED}。
 * 任何状态流转都在服务端校验前置状态，前端传什么状态都不采信。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketOrderServiceImpl extends ServiceImpl<TicketOrderMapper, TicketOrder> implements TicketOrderService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String STATUS_PENDING_PAY = "PENDING_PAY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REFUNDING = "REFUNDING";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 二维码签名前缀，与订单号拼接后取摘要，防止伪造二维码 */
    private static final String QR_SIGN_PREFIX = "TICKET:";
    private static final int QR_SIGN_LENGTH = 16;
    private static final int DEFAULT_RANGE_DAYS = 30;

    /** 统一离园时刻：当日 18:00 后核销只记入园流水，在园人数不再增加（默认 6 点后出园） */
    private static final int LEAVE_HOUR = 18;
    /** 客流预警阈值（饱和度百分比），与 PassengerFlowServiceImpl 保持同一口径 */
    private static final double FLOW_WARNING_THRESHOLD = 70D;
    private static final double FLOW_DANGER_THRESHOLD = 90D;

    private final TicketStockMapper ticketStockMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final PassengerFlowMapper passengerFlowMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final TouristMapper touristMapper;
    private final SysUserMapper sysUserMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public PageResult<TicketOrderVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                               String status, String channel, Long ticketTypeId,
                                               LocalDate playDateStart, LocalDate playDateEnd,
                                               LocalDate createDateStart, LocalDate createDateEnd) {
        Page<TicketOrder> page = this.page(new Page<>(pageNum, pageSize),
                buildWrapper(keyword, scenicId, status, channel, ticketTypeId,
                        playDateStart, playDateEnd, createDateStart, createDateEnd)
                        .orderByDesc(TicketOrder::getCreateTime));
        // 名称回填是批量做的，因此不能走 PageResult.of(page, 单条映射)——
        // 那样每条记录都会各触发一次关联查询
        return new PageResult<>(toVOList(page.getRecords()), page.getTotal(),
                page.getCurrent(), page.getSize());
    }

    @Override
    public TicketOrderVO getDetail(Long id) {
        TicketOrder order = requireOrder(id);
        return toVOList(Collections.singletonList(order)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long orderId, String gate) {
        doVerify(requireOrder(orderId), gate, "MANUAL");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyByCode(String code, String gate) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("请提供二维码内容或订单号");
        }
        String orderNo;
        try {
            orderNo = parseOrderNoFromCode(code.trim());
        } catch (BusinessException e) {
            // 签名对不上的票最需要留痕——这是识别伪造门票的唯一线索
            writeFailRecord(null, gate, "QRCODE", e.getMessage());
            throw e;
        }
        TicketOrder order = this.getOne(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getOrderNo, orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            writeFailRecord(null, gate, "QRCODE", "订单号不存在：" + orderNo);
            throw new BusinessException("未找到对应订单：" + orderNo);
        }
        doVerify(order, gate, "QRCODE");
    }

    /** 核销核心逻辑：状态与游玩日期都校验通过后才置为已核销 */
    private void doVerify(TicketOrder order, String gate, String verifyType) {
        ScenicScope.checkWritable(order.getScenicId());

        if (!STATUS_PAID.equals(order.getStatus())) {
            String message = "订单当前状态为「" + statusText(order.getStatus()) + "」，只有已支付订单才能核销";
            writeFailRecord(order, gate, verifyType, message);
            throw new BusinessException(message);
        }
        // 未到游玩日期就放行会造成「明天的票今天入园」，客流统计也会跟着失真
        if (order.getPlayDate() != null && order.getPlayDate().isAfter(LocalDate.now())) {
            String message = "尚未到游玩日期（" + order.getPlayDate() + "），暂不能核销";
            writeFailRecord(order, gate, verifyType, message);
            throw new BusinessException(message);
        }

        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus(STATUS_VERIFIED);
        update.setVerifyTime(LocalDateTime.now());
        updateById(update);

        writeCheckinRecord(order, gate, verifyType, "SUCCESS", null);
        // 业务闭环：核销即入园，同步滚动当日客流时段表，
        // 驱动客流概览、客流报表、游客端首页实时人数联动
        applyFlow(order);
        log.info("订单核销成功：orderNo={}，gate={}，verifyType={}，operatorId={}",
                order.getOrderNo(), gate, verifyType, UserContext.getUserId());
    }

    /**
     * 核销后滚动当日客流统计：入园人数、在园人数按小时累计到 passenger_flow。
     *
     * <p>统计口径：核销即入园；当日 18:00（统一离园时刻）之后的核销只记入园流水、
     * 不再增加在园人数（视为当天闭园前离园）。写入失败只告警不回滚，核销主流程不受影响。</p>
     */
    private void applyFlow(TicketOrder order) {
        try {
            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour();
            boolean afterLeave = hour >= LEAVE_HOUR;
            int qty = order.getQuantity() == null ? 0 : order.getQuantity();

            PassengerFlow row = passengerFlowMapper.selectOne(Wrappers.<PassengerFlow>lambdaQuery()
                    .eq(PassengerFlow::getScenicId, order.getScenicId())
                    .eq(PassengerFlow::getStatDate, today)
                    .eq(PassengerFlow::getStatHour, hour)
                    .last("LIMIT 1"));
            if (row == null) {
                PassengerFlow insert = new PassengerFlow();
                insert.setScenicId(order.getScenicId());
                insert.setStatDate(today);
                insert.setStatHour(hour);
                insert.setEnterCount(qty);
                // 离园数统一由 18 点闭园行承载（见 PassengerFlowServiceImpl#closeDayAfterLeave），
                // 这里只累计入园流水，避免离园数重复统计
                insert.setLeaveCount(0);
                insert.setCurrentCount(afterLeave ? 0 : qty);
                int capacity = defaultCapacity(order.getScenicId());
                insert.setCapacity(capacity);
                insert.setWarningLevel(flowLevel(afterLeave ? 0 : qty, capacity));
                passengerFlowMapper.insert(insert);
            } else {
                PassengerFlow update = new PassengerFlow();
                update.setId(row.getId());
                update.setEnterCount(nz(row.getEnterCount()) + qty);
                update.setCurrentCount(afterLeave ? 0 : nz(row.getCurrentCount()) + qty);
                passengerFlowMapper.updateById(update);
            }
        } catch (Exception e) {
            log.warn("核销后滚动客流统计失败（不影响核销）：orderNo={}", order.getOrderNo(), e);
        }
    }

    /** 景区日承载量作为时段容量口径；未配置时按 0 处理（饱和度按 0 计，不打扰预警） */
    private int defaultCapacity(Long scenicId) {
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        return area == null || area.getDailyCapacity() == null ? 0 : area.getDailyCapacity();
    }

    private String flowLevel(int current, int capacity) {
        if (capacity <= 0) {
            return "NORMAL";
        }
        double rate = current * 100.0 / capacity;
        if (rate >= FLOW_DANGER_THRESHOLD) {
            return "DANGER";
        }
        return rate >= FLOW_WARNING_THRESHOLD ? "WARNING" : "NORMAL";
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * 写入失败检票记录
     *
     * <p>外层事务会因为校验失败而回滚，如果失败记录跟着一起回滚，
     * 等于「没检过票也没留痕」——异常刷卡恰恰是最需要留存的数据。
     * 因此这里开一个独立事务提交。</p>
     */
    private void writeFailRecord(TicketOrder order, String gate, String verifyType, String reason) {
        transactionTemplate.executeWithoutResult(status ->
                writeCheckinRecord(order, gate, verifyType, "FAIL", reason));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditRefund(RefundAuditDTO dto) {
        TicketOrder order = requireOrder(dto.getOrderId());
        ScenicScope.checkWritable(order.getScenicId());
        if (!STATUS_REFUNDING.equals(order.getStatus())) {
            throw new BusinessException("订单当前状态为「" + statusText(order.getStatus()) + "」，不存在待审核的退票申请");
        }
        if (Boolean.TRUE.equals(dto.getApproved())) {
            approveRefund(order, dto.getRemark());
        } else {
            rejectRefund(order, dto.getRemark());
        }
    }

    @Override
    public OrderStatVO statistics(Long scenicId, LocalDate startDate, LocalDate endDate) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(DEFAULT_RANGE_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }

        List<TicketOrder> orders = this.list(Wrappers.<TicketOrder>lambdaQuery()
                .eq(scopeScenicId != null, TicketOrder::getScenicId, scopeScenicId)
                .ge(TicketOrder::getCreateTime, start.atStartOfDay())
                .le(TicketOrder::getCreateTime, LocalDateTime.of(end, LocalTime.MAX)));

        OrderStatVO stat = new OrderStatVO();
        stat.setTotalCount((long) orders.size());

        Map<String, Long> statusCount = new LinkedHashMap<>();
        Map<String, Long> channelCount = new LinkedHashMap<>();
        Map<String, Long> dailyCount = new LinkedHashMap<>();
        Map<String, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        String today = LocalDate.now().format(DATE_FORMAT);
        BigDecimal todayRevenue = BigDecimal.ZERO;

        for (TicketOrder order : orders) {
            String status = order.getStatus();
            statusCount.merge(statusText(status), 1L, Long::sum);
            channelCount.merge(channelText(order.getChannel()), 1L, Long::sum);

            BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            // 营收口径：只统计「已支付且未退款」的订单。把退款中的金额算进营收，
            // 报表会虚高，财务对账时无法解释
            if (STATUS_PAID.equals(status) || STATUS_VERIFIED.equals(status)) {
                totalRevenue = totalRevenue.add(amount);
            }
            if (order.getCreateTime() != null) {
                String day = order.getCreateTime().toLocalDate().format(DATE_FORMAT);
                dailyCount.merge(day, 1L, Long::sum);
                dailyRevenue.merge(day, amount, BigDecimal::add);
                if (today.equals(day) && (STATUS_PAID.equals(status) || STATUS_VERIFIED.equals(status))) {
                    todayRevenue = todayRevenue.add(amount);
                }
            }
        }

        stat.setPendingPayCount(statusCount.getOrDefault(statusText(STATUS_PENDING_PAY), 0L));
        stat.setPaidCount(statusCount.getOrDefault(statusText(STATUS_PAID), 0L));
        stat.setVerifiedCount(statusCount.getOrDefault(statusText(STATUS_VERIFIED), 0L));
        stat.setRefundingCount(statusCount.getOrDefault(statusText(STATUS_REFUNDING), 0L));
        stat.setRefundedCount(statusCount.getOrDefault(statusText(STATUS_REFUNDED), 0L));
        stat.setCancelledCount(statusCount.getOrDefault(statusText(STATUS_CANCELLED), 0L));
        stat.setTotalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP));
        stat.setTodayRevenue(todayRevenue.setScale(2, RoundingMode.HALF_UP));
        stat.setTodayOrderCount(dailyCount.getOrDefault(today, 0L));

        long paidTotal = stat.getPaidCount() + stat.getVerifiedCount()
                + stat.getRefundingCount() + stat.getRefundedCount();
        stat.setRefundRate(rate(stat.getRefundedCount() + stat.getRefundingCount(), paidTotal));

        stat.setOrderTrend(buildCountTrend(dailyCount, start, end));
        stat.setRevenueTrend(buildRevenueTrend(dailyRevenue, start, end));
        stat.setStatusDist(toStatItems(statusCount));
        stat.setChannelDist(toStatItems(channelCount));
        return stat;
    }

    @Override
    public List<TicketOrderVO> listForExport(String keyword, Long scenicId, String status, String channel,
                                             LocalDate playDateStart, LocalDate playDateEnd, int limit) {
        int max = limit <= 0 ? 5000 : Math.min(limit, 20000);
        List<TicketOrder> orders = this.list(buildWrapper(keyword, scenicId, status, channel, null,
                playDateStart, playDateEnd, null, null)
                .orderByDesc(TicketOrder::getCreateTime)
                .last("LIMIT " + max));
        return toVOList(orders);
    }

    /* ==================== 私有方法 ==================== */

    private LambdaQueryWrapper<TicketOrder> buildWrapper(
            String keyword, Long scenicId, String status, String channel, Long ticketTypeId,
            LocalDate playDateStart, LocalDate playDateEnd,
            LocalDate createDateStart, LocalDate createDateEnd) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        return Wrappers.<TicketOrder>lambdaQuery()
                .eq(scopeScenicId != null, TicketOrder::getScenicId, scopeScenicId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(TicketOrder::getOrderNo, keyword).or()
                        .like(TicketOrder::getContactName, keyword).or()
                        .like(TicketOrder::getContactPhone, keyword))
                .eq(StringUtils.hasText(status), TicketOrder::getStatus, status)
                .eq(StringUtils.hasText(channel), TicketOrder::getChannel, channel)
                .eq(ticketTypeId != null, TicketOrder::getTicketTypeId, ticketTypeId)
                .ge(playDateStart != null, TicketOrder::getPlayDate, playDateStart)
                .le(playDateEnd != null, TicketOrder::getPlayDate, playDateEnd)
                .ge(createDateStart != null, TicketOrder::getCreateTime,
                        createDateStart == null ? null : createDateStart.atStartOfDay())
                .le(createDateEnd != null, TicketOrder::getCreateTime,
                        createDateEnd == null ? null : LocalDateTime.of(createDateEnd, LocalTime.MAX));
    }

    private TicketOrder requireOrder(Long id) {
        TicketOrder order = id == null ? null : getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    /** 审核通过：退款并回滚该票种对应游玩日期的已售库存 */
    private void approveRefund(TicketOrder order, String remark) {
        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus(STATUS_REFUNDED);
        update.setRefundTime(LocalDateTime.now());
        update.setRefundAuditBy(UserContext.getUserId());
        update.setRefundAuditTime(LocalDateTime.now());
        update.setRefundAuditRemark(StringUtils.hasText(remark) ? remark : "审核通过");
        updateById(update);

        // 退款成功后这张票要重新变回可售，不回滚会出现「票已退钱却永远买不到」。
        // 走 rollbackSold 而不是「先查再改」：SQL 里用 GREATEST 兜底防负数，且天然带上时段条件——
        // 分时票若只按 (票种,日期) 查会命中任意时段的行，把别的时段库存扣错。
        if (order.getQuantity() != null && order.getQuantity() > 0) {
            ticketStockMapper.rollbackSold(order.getTicketTypeId(), order.getPlayDate(),
                    TimeSlotUtil.normalize(order.getTimeSlot()), order.getQuantity());
        }
        log.info("退票审核通过：orderNo={}，auditBy={}", order.getOrderNo(), UserContext.getUserId());
    }

    /** 审核驳回：订单退回已支付，游客可继续使用 */
    private void rejectRefund(TicketOrder order, String remark) {
        if (!StringUtils.hasText(remark)) {
            throw new BusinessException("驳回退票申请时必须填写审核意见");
        }
        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus(STATUS_PAID);
        update.setRefundAuditBy(UserContext.getUserId());
        update.setRefundAuditTime(LocalDateTime.now());
        update.setRefundAuditRemark(remark);
        updateById(update);
        log.info("退票审核驳回：orderNo={}，原因={}", order.getOrderNo(), remark);
    }

    private void writeCheckinRecord(TicketOrder order, String gate, String verifyType,
                                    String status, String failReason) {
        CheckinRecord record = new CheckinRecord();
        if (order != null) {
            record.setOrderId(order.getId());
            record.setOrderNo(order.getOrderNo());
            record.setScenicId(order.getScenicId());
            record.setTicketName(order.getTicketName());
            record.setQuantity(order.getQuantity());
        }
        record.setGate(StringUtils.hasText(gate) ? gate : "默认检票口");
        record.setVerifyType(verifyType);
        record.setStatus(status);
        record.setFailReason(failReason);
        record.setVerifyTime(LocalDateTime.now());
        checkinRecordMapper.insert(record);
    }

    /** 生成二维码内容：订单号 + 摘要签名，防止手工拼一个订单号就能伪造门票 */
    public static String buildQrCode(String orderNo) {
        return orderNo + "." + SensitiveUtil.sha256(QR_SIGN_PREFIX + orderNo).substring(0, QR_SIGN_LENGTH);
    }

    /**
     * 从扫码结果解析订单号
     *
     * <p>带签名的按签名校验；不含 {@code .} 的按纯订单号处理，
     * 兼容检票员手工输入订单号核销的场景。</p>
     */
    private String parseOrderNoFromCode(String code) {
        int idx = code.lastIndexOf('.');
        if (idx <= 0) {
            return code;
        }
        String orderNo = code.substring(0, idx);
        String sign = code.substring(idx + 1);
        String expect = SensitiveUtil.sha256(QR_SIGN_PREFIX + orderNo).substring(0, QR_SIGN_LENGTH);
        if (!expect.equalsIgnoreCase(sign)) {
            throw new BusinessException("二维码校验失败，该票据可能被伪造");
        }
        return orderNo;
    }

    /** 批量填充关联名称，避免逐条查询造成 N+1 */
    private List<TicketOrderVO> toVOList(List<TicketOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> scenicIds = orders.stream().map(TicketOrder::getScenicId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> touristIds = orders.stream().map(TicketOrder::getTouristId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> auditorIds = orders.stream().map(TicketOrder::getRefundAuditBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> scenicNames = scenicIds.isEmpty() ? Collections.emptyMap()
                : scenicAreaMapper.selectBatchIds(scenicIds).stream()
                .collect(Collectors.toMap(ScenicArea::getId, ScenicArea::getScenicName, (a, b) -> a));
        Map<Long, String> touristNames = touristIds.isEmpty() ? Collections.emptyMap()
                : touristMapper.selectBatchIds(touristIds).stream()
                .collect(Collectors.toMap(Tourist::getId, Tourist::getRealName, (a, b) -> a));
        Map<Long, String> auditorNames = auditorIds.isEmpty() ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(auditorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));

        return orders.stream().map(order -> {
            TicketOrderVO vo = new TicketOrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTouristId(order.getTouristId());
            vo.setTouristName(touristNames.get(order.getTouristId()));
            vo.setScenicId(order.getScenicId());
            vo.setScenicName(scenicNames.get(order.getScenicId()));
            vo.setTicketTypeId(order.getTicketTypeId());
            vo.setTicketName(order.getTicketName());
            vo.setQuantity(order.getQuantity());
            vo.setUnitPrice(order.getUnitPrice());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPlayDate(order.getPlayDate());
            vo.setTimeSlot(order.getTimeSlot());
            vo.setStatus(order.getStatus());
            vo.setStatusText(statusText(order.getStatus()));
            vo.setChannel(order.getChannel());
            vo.setChannelText(channelText(order.getChannel()));
            vo.setCreateTime(order.getCreateTime());
            vo.setPayTime(order.getPayTime());
            vo.setVerifyTime(order.getVerifyTime());
            vo.setExpireTime(order.getExpireTime());
            vo.setRefundApplyTime(order.getRefundApplyTime());
            vo.setRefundReason(order.getRefundReason());
            vo.setRefundAuditRemark(order.getRefundAuditRemark());
            vo.setRefundAuditByName(auditorNames.get(order.getRefundAuditBy()));
            vo.setQrCode(order.getQrCode());
            vo.setContactName(order.getContactName());
            vo.setContactPhone(SensitiveUtil.maskPhone(order.getContactPhone()));
            vo.setRemark(order.getRemark());
            return vo;
        }).collect(Collectors.toList());
    }

    private List<TrendItemVO> buildCountTrend(Map<String, Long> dailyCount, LocalDate start, LocalDate end) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyCount.getOrDefault(key, 0L)));
        }
        return trend;
    }

    private List<TrendItemVO> buildRevenueTrend(Map<String, BigDecimal> dailyRevenue,
                                                LocalDate start, LocalDate end) {
        List<TrendItemVO> trend = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyRevenue.getOrDefault(key, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP)));
        }
        return trend;
    }

    private List<StatItemVO> toStatItems(Map<String, Long> countMap) {
        List<StatItemVO> items = new ArrayList<>();
        countMap.forEach((name, value) -> items.add(new StatItemVO(name, value)));
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return items;
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** 状态码 → 中文，前后端展示、导出、统计共用同一套文案 */
    public static String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING_PAY -> "待支付";
            case STATUS_PAID -> "已支付";
            case STATUS_VERIFIED -> "已核销";
            case STATUS_REFUNDING -> "退款中";
            case STATUS_REFUNDED -> "已退款";
            case STATUS_CANCELLED -> "已取消";
            default -> status;
        };
    }

    public static String channelText(String channel) {
        if (channel == null) {
            return "未知";
        }
        return switch (channel) {
            case "MINI_PROGRAM" -> "小程序";
            case "OTA" -> "OTA平台";
            case "WINDOW" -> "售票窗口";
            case "AGENCY" -> "旅行社";
            default -> channel;
        };
    }
}
