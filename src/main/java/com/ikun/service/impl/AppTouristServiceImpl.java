package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.common.ResultCode;
import com.ikun.dto.AppTouristUpdateDTO;
import com.ikun.entity.Complaint;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.Tourist;
import com.ikun.mapper.ComplaintMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.AppTouristService;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.AppProfileVO;
import com.ikun.vo.TouristVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 游客小程序个人中心服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppTouristServiceImpl implements AppTouristService {

    private static final String STATUS_PENDING_PAY = "PENDING_PAY";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REFUNDING = "REFUNDING";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String STATUS_COMPLAINT_PENDING = "PENDING";
    private static final String STATUS_COMPLAINT_PROCESSING = "PROCESSING";

    private final TouristMapper touristMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final ComplaintMapper complaintMapper;

    @Override
    public AppProfileVO profile() {
        Long touristId = requireTouristId();
        Tourist tourist = touristMapper.selectById(touristId);
        if (tourist == null) {
            throw new BusinessException("账号不存在或已被删除");
        }

        AppProfileVO vo = new AppProfileVO();
        vo.setTourist(toVO(tourist));
        vo.setTotalOrderCount(countOrder(touristId, null));
        vo.setPendingPayCount(countOrder(touristId, STATUS_PENDING_PAY));
        vo.setPaidCount(countOrder(touristId, STATUS_PAID));
        vo.setVerifiedCount(countOrder(touristId, STATUS_VERIFIED));
        // 退款订单 = 退款中 + 已退款，二者都是「因退款而产生的记录」
        vo.setRefundCount(countOrderIn(touristId, STATUS_REFUNDING, STATUS_REFUNDED));
        vo.setComplaintCount(countComplaint(touristId, null));
        vo.setProcessingComplaintCount(countComplaintIn(touristId,
                STATUS_COMPLAINT_PENDING, STATUS_COMPLAINT_PROCESSING));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(AppTouristUpdateDTO dto) {
        Long touristId = requireTouristId();
        Tourist exist = touristMapper.selectById(touristId);
        if (exist == null) {
            throw new BusinessException("账号不存在或已被删除");
        }

        Tourist update = new Tourist();
        update.setId(touristId);
        if (StringUtils.hasText(dto.getRealName())) {
            update.setRealName(dto.getRealName().trim());
        }
        if (dto.getGender() != null) {
            update.setGender(dto.getGender());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            update.setAvatar(dto.getAvatar().trim());
        }
        // 首次补全证件号即视为完成实名认证，无需再加一道人工审核
        if (StringUtils.hasText(dto.getIdCard())) {
            update.setIdCard(dto.getIdCard().trim());
            update.setRealNameStatus(1);
        }
        touristMapper.updateById(update);
        log.info("游客修改资料：touristId={}", touristId);
    }

    /* ==================== 私有方法 ==================== */

    private Long requireTouristId() {
        Long touristId = AppUserContext.getTouristId();
        if (touristId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录后再操作");
        }
        return touristId;
    }

    private Long countOrder(Long touristId, String status) {
        return ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getTouristId, touristId)
                .eq(StringUtils.hasText(status), TicketOrder::getStatus, status));
    }

    private Long countOrderIn(Long touristId, String... status) {
        return ticketOrderMapper.selectCount(Wrappers.<TicketOrder>lambdaQuery()
                .eq(TicketOrder::getTouristId, touristId)
                .in(TicketOrder::getStatus, (Object[]) status));
    }

    private Long countComplaint(Long touristId, String status) {
        return complaintMapper.selectCount(Wrappers.<Complaint>lambdaQuery()
                .eq(Complaint::getTouristId, touristId)
                .eq(StringUtils.hasText(status), Complaint::getStatus, status));
    }

    private Long countComplaintIn(Long touristId, String... status) {
        return complaintMapper.selectCount(Wrappers.<Complaint>lambdaQuery()
                .eq(Complaint::getTouristId, touristId)
                .in(Complaint::getStatus, (Object[]) status));
    }

    /** 实体转 VO：手机号、证件号一律脱敏后输出 */
    private TouristVO toVO(Tourist tourist) {
        TouristVO vo = new TouristVO();
        vo.setId(tourist.getId());
        vo.setTouristNo(tourist.getTouristNo());
        vo.setRealName(tourist.getRealName());
        vo.setPhone(SensitiveUtil.maskPhone(tourist.getPhone()));
        vo.setIdCard(maskIdCard(tourist.getIdCard()));
        vo.setGender(tourist.getGender());
        vo.setAvatar(tourist.getAvatar());
        vo.setSource(tourist.getSource());
        vo.setMemberLevel(tourist.getMemberLevel());
        vo.setPoints(tourist.getPoints());
        vo.setRealNameStatus(tourist.getRealNameStatus());
        vo.setIsBlacklist(tourist.getIsBlacklist());
        vo.setBlacklistReason(tourist.getBlacklistReason());
        vo.setBlacklistTime(tourist.getBlacklistTime());
        vo.setLastEnterTime(tourist.getLastEnterTime());
        vo.setRegisterScenicId(tourist.getRegisterScenicId());
        vo.setStatus(tourist.getStatus());
        vo.setCreateTime(tourist.getCreateTime());
        return vo;
    }

    /** 证件号脱敏：保留前 4 位与后 4 位，中间统一以 * 覆盖 */
    private String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard)) {
            return null;
        }
        String value = idCard.trim();
        if (value.length() <= 8) {
            return "****";
        }
        int middle = value.length() - 8;
        return value.substring(0, 4) + "*".repeat(middle) + value.substring(value.length() - 4);
    }
}
