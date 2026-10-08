package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ResultCode;
import com.ikun.dto.AppComplaintDTO;
import com.ikun.entity.Complaint;
import com.ikun.entity.ComplaintReply;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.TicketOrder;
import com.ikun.entity.Tourist;
import com.ikun.mapper.ComplaintMapper;
import com.ikun.mapper.ComplaintReplyMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.TicketOrderMapper;
import com.ikun.mapper.TouristMapper;
import com.ikun.service.AiClient;
import com.ikun.service.AppComplaintService;
import com.ikun.service.ComplaintService;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintVO;
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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游客小程序工单服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppComplaintServiceImpl implements AppComplaintService {

    private static final DateTimeFormatter NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final String TYPE_COMPLAINT = "COMPLAINT";
    private static final String TYPE_SUGGESTION = "SUGGESTION";
    private static final String TYPE_CONSULT = "CONSULT";

    private final ComplaintMapper complaintMapper;
    private final ComplaintReplyMapper complaintReplyMapper;
    private final TouristMapper touristMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final TicketOrderMapper ticketOrderMapper;
    private final AiClient aiClient;
    private final ComplaintService complaintService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(AppComplaintDTO dto) {
        Long touristId = requireTouristId();
        Tourist tourist = touristMapper.selectById(touristId);
        if (tourist == null) {
            throw new BusinessException("账号不存在或已被删除");
        }

        String orderNo = StringUtils.hasText(dto.getOrderNo()) ? dto.getOrderNo().trim() : null;
        Long scenicId = resolveScenicId(dto.getScenicId(), orderNo, tourist);
        String phone = StringUtils.hasText(dto.getPhone()) ? dto.getPhone().trim() : tourist.getPhone();

        // 提交即做一次情感分析：负面工单自动升级为紧急，避免情绪激烈的投诉被淹没
        String text = dto.getTitle() + "。" + dto.getContent();
        double score = aiClient.analyzeSentiment(text);
        String sentiment = sentimentLabel(score);

        Complaint complaint = new Complaint();
        complaint.setTicketNo(generateTicketNo());
        complaint.setScenicId(scenicId);
        complaint.setTouristId(touristId);
        complaint.setTouristName(tourist.getRealName());
        complaint.setPhone(phone);
        complaint.setOrderNo(orderNo);
        complaint.setTitle(dto.getTitle().trim());
        complaint.setContent(dto.getContent().trim());
        complaint.setType(normalizeType(dto.getType()));
        complaint.setPriority("NEGATIVE".equals(sentiment) ? "URGENT" : "NORMAL");
        complaint.setStatus(STATUS_PENDING);
        complaint.setSentiment(sentiment);
        complaint.setSentimentScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        complaint.setSentimentTime(LocalDateTime.now());
        complaintMapper.insert(complaint);

        // 把游客的问题描述也写入回复流，详情页的沟通记录才完整
        ComplaintReply reply = new ComplaintReply();
        reply.setComplaintId(complaint.getId());
        reply.setReplyType("TOURIST");
        reply.setContent(dto.getContent().trim());
        reply.setReplyBy(tourist.getRealName());
        reply.setSentiment(sentiment);
        complaintReplyMapper.insert(reply);

        log.info("小程序提交工单：ticketNo={}，touristId={}，sentiment={}",
                complaint.getTicketNo(), touristId, sentiment);
    }

    @Override
    public PageResult<ComplaintVO> myComplaints(Integer pageNum, Integer pageSize, String status) {
        Long touristId = requireTouristId();
        Page<Complaint> page = complaintMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Complaint>lambdaQuery()
                        .eq(Complaint::getTouristId, touristId)
                        .eq(StringUtils.hasText(status), Complaint::getStatus, status)
                        .orderByDesc(Complaint::getCreateTime));

        List<Complaint> records = page.getRecords();
        Set<Long> scenicIds = records.stream().map(Complaint::getScenicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var scenicNames = scenicIds.isEmpty() ? java.util.Collections.<Long, String>emptyMap()
                : scenicAreaMapper.selectBatchIds(scenicIds).stream()
                .collect(Collectors.toMap(ScenicArea::getId, ScenicArea::getScenicName, (a, b) -> a));
        List<ComplaintVO> list = records.stream()
                .map(c -> toVO(c, scenicNames.get(c.getScenicId())))
                .collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ComplaintDetailVO myComplaintDetail(Long id) {
        requireOwnComplaint(id);
        // 详情复用后台的组装逻辑（含回复列表），归属已在上面校验过
        return complaintService.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendReply(Long id, String content) {
        Complaint complaint = requireOwnComplaint(id);
        if (STATUS_CLOSED.equals(complaint.getStatus())) {
            throw new BusinessException("工单已完结，如需继续沟通请重新提交");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("回复内容不能为空");
        }

        ComplaintReply reply = new ComplaintReply();
        reply.setComplaintId(id);
        reply.setReplyType("TOURIST");
        reply.setContent(content.trim());
        reply.setReplyBy(complaint.getTouristName());
        reply.setSentiment(sentimentLabel(aiClient.analyzeSentiment(content)));
        complaintReplyMapper.insert(reply);

        // 游客补充了信息，说明问题仍在跟进中，把待处理工单推进为处理中
        if (STATUS_PENDING.equals(complaint.getStatus())) {
            Complaint update = new Complaint();
            update.setId(id);
            update.setStatus(STATUS_PROCESSING);
            complaintMapper.updateById(update);
        }
        log.info("游客追加工单回复：complaintId={}", id);
    }

    /* ==================== 私有方法 ==================== */

    private Long requireTouristId() {
        Long touristId = AppUserContext.getTouristId();
        if (touristId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录后再操作");
        }
        return touristId;
    }

    /** 加载并校验工单归属，非本人工单一律当作「不存在」 */
    private Complaint requireOwnComplaint(Long id) {
        Long touristId = requireTouristId();
        Complaint complaint = id == null ? null : complaintMapper.selectById(id);
        if (complaint == null || !Objects.equals(complaint.getTouristId(), touristId)) {
            throw new BusinessException("工单不存在或无权查看");
        }
        return complaint;
    }

    /**
     * 确定工单归属景区。
     *
     * <p>{@code complaint.scenic_id} 非空，按「显式指定 → 关联订单所属景区 →
     * Token 中的景区 → 注册景区 → 首个启用景区」的顺序兜底，避免因缺景区而提交失败。</p>
     */
    private Long resolveScenicId(Long requested, String orderNo, Tourist tourist) {
        if (requested != null) {
            return requested;
        }
        if (StringUtils.hasText(orderNo)) {
            TicketOrder order = ticketOrderMapper.selectOne(Wrappers.<TicketOrder>lambdaQuery()
                    .eq(TicketOrder::getOrderNo, orderNo)
                    .last("LIMIT 1"));
            if (order != null) {
                // 关联订单必须属于当前游客，防止拿别人的订单号提交工单
                if (!Objects.equals(order.getTouristId(), tourist.getId())) {
                    throw new BusinessException("关联订单不属于当前账号");
                }
                return order.getScenicId();
            }
        }
        if (AppUserContext.getScenicId() != null) {
            return AppUserContext.getScenicId();
        }
        if (tourist.getRegisterScenicId() != null) {
            return tourist.getRegisterScenicId();
        }
        ScenicArea first = scenicAreaMapper.selectOne(Wrappers.<ScenicArea>lambdaQuery()
                .eq(ScenicArea::getStatus, "ENABLE")
                .orderByAsc(ScenicArea::getId)
                .last("LIMIT 1"));
        if (first == null) {
            throw new BusinessException("系统尚未配置可用景区，无法提交工单");
        }
        return first.getId();
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return TYPE_COMPLAINT;
        }
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case TYPE_SUGGESTION, TYPE_CONSULT, TYPE_COMPLAINT -> upper;
            default -> TYPE_COMPLAINT;
        };
    }

    /** 工单编号：TK-yyyyMMdd-序号，如 TK-20250910-001 */
    private String generateTicketNo() {
        Long todayCount = complaintMapper.selectCount(Wrappers.<Complaint>lambdaQuery()
                .ge(Complaint::getCreateTime, LocalDate.now().atStartOfDay()));
        long seq = (todayCount == null ? 0L : todayCount) + 1L;
        return "TK-" + LocalDate.now().format(NO_DATE_FORMAT) + "-" + String.format("%03d", seq);
    }

    private String sentimentLabel(double score) {
        if (score >= 0.2) {
            return "POSITIVE";
        }
        return score <= -0.2 ? "NEGATIVE" : "NEUTRAL";
    }

    private ComplaintVO toVO(Complaint complaint, String scenicName) {
        ComplaintVO vo = new ComplaintVO();
        vo.setId(complaint.getId());
        vo.setTicketNo(complaint.getTicketNo());
        vo.setScenicId(complaint.getScenicId());
        vo.setScenicName(scenicName);
        vo.setTouristId(complaint.getTouristId());
        vo.setTouristName(complaint.getTouristName());
        vo.setPhone(SensitiveUtil.maskPhone(complaint.getPhone()));
        vo.setOrderNo(complaint.getOrderNo());
        vo.setTitle(complaint.getTitle());
        vo.setContent(complaint.getContent());
        vo.setType(complaint.getType());
        vo.setTypeText(typeText(complaint.getType()));
        vo.setPriority(complaint.getPriority());
        vo.setPriorityText("URGENT".equals(complaint.getPriority()) ? "紧急" : "普通");
        vo.setStatus(complaint.getStatus());
        vo.setStatusText(statusText(complaint.getStatus()));
        vo.setSentiment(complaint.getSentiment());
        vo.setSentimentText(sentimentText(complaint.getSentiment()));
        vo.setSentimentScore(complaint.getSentimentScore());
        vo.setSentimentTime(complaint.getSentimentTime());
        vo.setHandlerId(complaint.getHandlerId());
        vo.setHandleTime(complaint.getHandleTime());
        vo.setCreateTime(complaint.getCreateTime());
        return vo;
    }

    private String typeText(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case TYPE_COMPLAINT -> "投诉";
            case TYPE_SUGGESTION -> "建议";
            case TYPE_CONSULT -> "咨询";
            default -> type;
        };
    }

    private String statusText(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_PROCESSING -> "处理中";
            case STATUS_CLOSED -> "已完结";
            default -> status;
        };
    }

    private String sentimentText(String sentiment) {
        if (sentiment == null) {
            return "未分析";
        }
        return switch (sentiment) {
            case "POSITIVE" -> "积极";
            case "NEGATIVE" -> "消极";
            case "NEUTRAL" -> "中性";
            default -> sentiment;
        };
    }
}
