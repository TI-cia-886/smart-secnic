package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ResultCode;
import com.ikun.dto.AppOrderCreateDTO;
import com.ikun.dto.AppRefundDTO;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.TicketStock;
import com.ikun.entity.TicketType;
import com.ikun.entity.Tourist;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TicketStockMapper;
import com.ikun.mapper.TicketTypeMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.AppOrderService;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.TicketOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游客小程序订单服务实现
 *
 * <p>下单、支付、退票、取消都在服务端校验订单归属与状态前置条件，
 * 前端传什么状态一概不采信。库存扣减走 {@code ticket_stock} 的条件更新，
 * 保证并发下不会超卖。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppOrderServiceImpl implements AppOrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String STATUS_PENDING_PAY = "PENDING_PAY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_REFUNDING = "REFUNDING";

    private static final String CHANNEL_MINI_PROGRAM = "MINI_PROGRAM";

    /** 未支付订单的保留时长（分钟），超时需重新下单 */
    private static final int PAY_EXPIRE_MINUTES = 30;
    /** 最多可提前预订的天数 */
    private static final int MAX_BOOK_AHEAD_DAYS = 90;
    /** 单笔订单最多购票数量 */
    private static final int MAX_QUANTITY_PER_ORDER = 20;

    private final TicketOrderMapper ticketOrderMapper;
    private final TicketTypeMapper ticketTypeMapper;
    private final TicketStockMapper ticketStockMapper;
    private final TouristMapper touristMapper;
    private final ScenicAreaMapper scenicAreaMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketOrderVO create(AppOrderCreateDTO dto) {
        Long touristId = requireTouristId();
        Tourist tourist = touristMapper.selectById(touristId);
        if (tourist == null) {
            throw new BusinessException("账号不存在或已被删除");
        }
        if (tourist.getIsBlacklist() != null && tourist.getIsBlacklist() == 1) {
            throw new BusinessException("账号已被列入黑名单，暂无法购票");
        }

        TicketType type = ticketTypeMapper.selectById(dto.getTicketTypeId());
        if (type == null) {
            throw new BusinessException("票种不存在");
        }
        if (type.getStatus() == null || type.getStatus() != 1) {
            throw new BusinessException("该票种已下架，暂不可购买");
        }

        LocalDate playDate = dto.getPlayDate();
        if (playDate.isBefore(LocalDate.now())) {
            throw new BusinessException("游玩日期不能早于今天");
        }
        if (playDate.isAfter(LocalDate.now().plusDays(MAX_BOOK_AHEAD_DAYS))) {
            throw new BusinessException("仅支持预订 " + MAX_BOOK_AHEAD_DAYS + " 天以内的门票");
        }
        int quantity = dto.getQuantity();
        if (quantity > MAX_QUANTITY_PER_ORDER) {
            throw new BusinessException("单笔订单最多购买 " + MAX_QUANTITY_PER_ORDER + " 张门票");
        }

        ensureStock(type, playDate);
        // 条件更新一步完成「判断余量 + 扣减」，0 行即库存不足，避免先查后改导致超卖
        int locked = ticketStockMapper.lockStock(type.getId(), playDate, quantity);
        if (locked == 0) {
            throw new BusinessException(ResultCode.TICKET_STOCK_NOT_ENOUGH,
                    "该日期门票库存不足，请选择其他日期");
        }

        String orderNo = generateOrderNo();
        BigDecimal unitPrice = type.getPrice() == null ? BigDecimal.ZERO : type.getPrice();
        TicketOrder order = new TicketOrder();
        order.setOrderNo(orderNo);
        order.setTouristId(touristId);
        order.setScenicId(type.getScenicId());
        order.setTicketTypeId(type.getId());
        order.setTicketName(type.getTicketName());
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice.setScale(2, RoundingMode.HALF_UP));
        order.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        order.setPlayDate(playDate);
        order.setStatus(STATUS_PENDING_PAY);
        order.setChannel(CHANNEL_MINI_PROGRAM);
        order.setExpireTime(LocalDateTime.now().plusMinutes(PAY_EXPIRE_MINUTES));
        order.setContactName(dto.getContactName().trim());
        order.setContactPhone(dto.getContactPhone().trim());
        order.setRemark(dto.getRemark());
        order.setQrCode(TicketOrderServiceImpl.buildQrCode(orderNo));
        ticketOrderMapper.insert(order);

        log.info("小程序下单成功：orderNo={}，touristId={}，quantity={}", orderNo, touristId, quantity);
        return toVO(order, tourist.getRealName(), scenicName(order.getScenicId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketOrderVO pay(Long orderId) {
        TicketOrder order = requireOwnOrder(orderId);
        if (!STATUS_PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException("订单当前状态为「" + TicketOrderServiceImpl.statusText(order.getStatus())
                    + "」，无需支付");
        }
        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BusinessException("订单已超时失效，请重新下单");
        }

        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus(STATUS_PAID);
        update.setPayTime(LocalDateTime.now());
        ticketOrderMapper.updateById(update);
        // 锁定库存转为已售
        ticketStockMapper.confirmStock(order.getTicketTypeId(), order.getPlayDate(), order.getQuantity());

        order.setStatus(STATUS_PAID);
        order.setPayTime(update.getPayTime());
        log.info("小程序订单支付成功：orderNo={}", order.getOrderNo());
        return toVO(order, touristName(order.getTouristId()), scenicName(order.getScenicId()));
    }

    @Override
    public PageResult<TicketOrderVO> myOrders(Integer pageNum, Integer pageSize, String status) {
        Long touristId = requireTouristId();
        Page<TicketOrder> page = ticketOrderMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<TicketOrder>lambdaQuery()
                        .eq(TicketOrder::getTouristId, touristId)
                        .eq(StringUtils.hasText(status), TicketOrder::getStatus, status)
                        .orderByDesc(TicketOrder::getCreateTime));
        List<TicketOrder> orders = page.getRecords();
        return new PageResult<>(toVOList(orders), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public TicketOrderVO myOrderDetail(Long orderId) {
        TicketOrder order = requireOwnOrder(orderId);
        return toVO(order, touristName(order.getTouristId()), scenicName(order.getScenicId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(Long orderId, AppRefundDTO dto) {
        TicketOrder order = requireOwnOrder(orderId);
        if (!STATUS_PAID.equals(order.getStatus())) {
            throw new BusinessException("仅已支付且未核销的订单可以申请退票");
        }

        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus(STATUS_REFUNDING);
        update.setRefundReason(dto.getReason().trim());
        update.setRefundApplyTime(LocalDateTime.now());
        ticketOrderMapper.updateById(update);
        log.info("小程序申请退票：orderNo={}，reason={}", order.getOrderNo(), dto.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        TicketOrder order = requireOwnOrder(orderId);
        if (!STATUS_PENDING_PAY.equals(order.getStatus())) {
            throw new BusinessException("仅待支付订单可以取消");
        }

        TicketOrder update = new TicketOrder();
        update.setId(order.getId());
        update.setStatus("CANCELLED");
        ticketOrderMapper.updateById(update);
        // 取消订单必须释放锁定库存，否则这些票会一直被占用而卖不出去
        ticketStockMapper.releaseStock(order.getTicketTypeId(), order.getPlayDate(), order.getQuantity());
        log.info("小程序取消订单：orderNo={}", order.getOrderNo());
    }

    /* ==================== 私有方法 ==================== */

    private Long requireTouristId() {
        Long touristId = AppUserContext.getTouristId();
        if (touristId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录后再操作");
        }
        return touristId;
    }

    /** 加载并校验订单归属，非本人订单一律当作「不存在」处理，避免泄露他人订单信息 */
    private TicketOrder requireOwnOrder(Long orderId) {
        Long touristId = requireTouristId();
        TicketOrder order = orderId == null ? null : ticketOrderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getTouristId(), touristId)) {
            throw new BusinessException("订单不存在或无权查看");
        }
        return order;
    }

    /**
     * 确保目标日期的库存记录存在。
     *
     * <p>{@code ticket_type.stock} 只是每日库存默认值，真正按天扣减依赖 {@code ticket_stock}。
     * 首次预订某一天时按票种默认库存初始化一条记录。</p>
     */
    private void ensureStock(TicketType type, LocalDate playDate) {
        TicketStock stock = ticketStockMapper.selectOne(Wrappers.<TicketStock>lambdaQuery()
                .eq(TicketStock::getTicketTypeId, type.getId())
                .eq(TicketStock::getStockDate, playDate)
                .last("LIMIT 1"));
        if (stock != null) {
            return;
        }
        TicketStock created = new TicketStock();
        created.setScenicId(type.getScenicId());
        created.setTicketTypeId(type.getId());
        created.setStockDate(playDate);
        created.setTotalStock(type.getStock() == null ? 0 : type.getStock());
        created.setSoldCount(0);
        created.setLockedCount(0);
        created.setStatus(1);
        ticketStockMapper.insert(created);
    }

    /** 订单号：ORD + 日期 + 当日序号，如 ORD20250910001 */
    private String generateOrderNo() {
        Long todayCount = ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .ge(TicketOrder::getCreateTime, LocalDate.now().atStartOfDay()));
        long seq = (todayCount == null ? 0L : todayCount) + 1L;
        return "ORD" + LocalDate.now().format(ORDER_NO_DATE) + String.format("%03d", seq);
    }

    private String touristName(Long touristId) {
        if (touristId == null) {
            return null;
        }
        Tourist tourist = touristMapper.selectById(touristId);
        return tourist == null ? null : tourist.getRealName();
    }

    private String scenicName(Long scenicId) {
        if (scenicId == null) {
            return null;
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        return area == null ? null : area.getScenicName();
    }

    private List<TicketOrderVO> toVOList(List<TicketOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> scenicIds = orders.stream().map(TicketOrder::getScenicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> scenicNames = scenicIds.isEmpty() ? Collections.emptyMap()
                : scenicAreaMapper.selectBatchIds(scenicIds).stream()
                .collect(Collectors.toMap(ScenicArea::getId, ScenicArea::getScenicName, (a, b) -> a));
        return orders.stream().map(order -> buildVO(order, scenicNames.get(order.getScenicId())))
                .collect(Collectors.toList());
    }

    private TicketOrderVO toVO(TicketOrder order, String touristName, String scenicName) {
        TicketOrderVO vo = buildVO(order, scenicName);
        vo.setTouristName(touristName);
        return vo;
    }

    private TicketOrderVO buildVO(TicketOrder order, String scenicName) {
        TicketOrderVO vo = new TicketOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTouristId(order.getTouristId());
        vo.setScenicId(order.getScenicId());
        vo.setScenicName(scenicName);
        vo.setTicketTypeId(order.getTicketTypeId());
        vo.setTicketName(order.getTicketName());
        vo.setQuantity(order.getQuantity());
        vo.setUnitPrice(order.getUnitPrice());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setPlayDate(order.getPlayDate());
        vo.setStatus(order.getStatus());
        vo.setStatusText(TicketOrderServiceImpl.statusText(order.getStatus()));
        vo.setChannel(order.getChannel());
        vo.setChannelText(TicketOrderServiceImpl.channelText(order.getChannel()));
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setVerifyTime(order.getVerifyTime());
        vo.setExpireTime(order.getExpireTime());
        vo.setRefundApplyTime(order.getRefundApplyTime());
        vo.setRefundReason(order.getRefundReason());
        vo.setRefundAuditRemark(order.getRefundAuditRemark());
        vo.setQrCode(order.getQrCode());
        vo.setContactName(order.getContactName());
        vo.setContactPhone(SensitiveUtil.maskPhone(order.getContactPhone()));
        vo.setRemark(order.getRemark());
        return vo;
    }
}
