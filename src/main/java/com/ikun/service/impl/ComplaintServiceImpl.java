package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.dto.ComplaintAssignDTO;
import com.ikun.dto.ComplaintReplyDTO;
import com.ikun.dto.ComplaintSummaryDTO;
import com.ikun.entity.Complaint;
import com.ikun.entity.ComplaintReply;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.SysUser;
import com.ikun.mapper.ComplaintMapper;
import com.ikun.mapper.ComplaintReplyMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.SysUserMapper;
import com.ikun.service.AiClient;
import com.ikun.service.ComplaintService;
import com.ikun.util.SensitiveUtil;
import com.ikun.vo.ComplaintDetailVO;
import com.ikun.vo.ComplaintReplyVO;
import com.ikun.vo.ComplaintSummaryVO;
import com.ikun.vo.ComplaintVO;
import com.ikun.vo.StatItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 投诉工单服务实现
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl extends ServiceImpl<ComplaintMapper, Complaint> implements ComplaintService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_CLOSED = "CLOSED";

    /** 归纳时统计的高频问题关键词：相比 n-gram，固定词典的结果可解释、可直接落到整改清单 */
    private static final List<String> ISSUE_KEYWORDS = List.of(
            "排队", "门票", "退票", "停车", "卫生", "厕所", "服务态度", "价格", "收费",
            "餐饮", "导览", "标识", "拥挤", "噪音", "安全", "插队", "讲解", "交通", "住宿", "预约");

    private final ComplaintReplyMapper complaintReplyMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final SysUserMapper sysUserMapper;
    private final AiClient aiClient;

    @Override
    public PageResult<ComplaintVO> pageQuery(Integer pageNum, Integer pageSize, String keyword, Long scenicId,
                                             String type, String priority, String status, String sentiment,
                                             LocalDate startDate, LocalDate endDate) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        LambdaQueryWrapper<Complaint> wrapper = Wrappers.<Complaint>lambdaQuery()
                .eq(scopeScenicId != null, Complaint::getScenicId, scopeScenicId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Complaint::getTicketNo, keyword).or()
                        .like(Complaint::getTitle, keyword).or()
                        .like(Complaint::getTouristName, keyword))
                .eq(StringUtils.hasText(type), Complaint::getType, type)
                .eq(StringUtils.hasText(priority), Complaint::getPriority, priority)
                .eq(StringUtils.hasText(status), Complaint::getStatus, status)
                .eq(StringUtils.hasText(sentiment), Complaint::getSentiment, sentiment)
                .ge(startDate != null, Complaint::getCreateTime,
                        startDate == null ? null : startDate.atStartOfDay())
                .le(endDate != null, Complaint::getCreateTime,
                        endDate == null ? null : LocalDateTime.of(endDate, LocalTime.MAX))
                .orderByDesc(Complaint::getCreateTime);

        Page<Complaint> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(toVOList(page.getRecords()), page.getTotal(),
                page.getCurrent(), page.getSize());
    }

    @Override
    public ComplaintDetailVO getDetail(Long id) {
        Complaint complaint = requireComplaint(id);
        ComplaintDetailVO vo = new ComplaintDetailVO();
        fill(vo, complaint, nameIndex(Collections.singletonList(complaint)));

        List<ComplaintReply> replies = complaintReplyMapper.selectList(Wrappers.<ComplaintReply>lambdaQuery()
                .eq(ComplaintReply::getComplaintId, id)
                .orderByAsc(ComplaintReply::getCreateTime));
        vo.setReplies(replies.stream().map(this::toReplyVO).collect(Collectors.toList()));
        vo.setReplyCount(replies.size());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long complaintId, ComplaintReplyDTO dto) {
        Complaint complaint = requireComplaint(complaintId);
        ScenicScope.checkWritable(complaint.getScenicId());
        if (STATUS_CLOSED.equals(complaint.getStatus())) {
            throw new BusinessException("工单已完结，如需继续沟通请重新开单");
        }

        ComplaintReply reply = new ComplaintReply();
        reply.setComplaintId(complaintId);
        reply.setReplyType("STAFF");
        reply.setContent(dto.getContent());
        reply.setReplyBy(StringUtils.hasText(UserContext.getUsername())
                ? UserContext.getUsername() : "客服");
        reply.setSentiment(sentimentLabel(aiClient.analyzeSentiment(dto.getContent())));
        complaintReplyMapper.insert(reply);

        // 只要客服回了话，工单就不再是「待处理」；否则列表会一直挂着未响应记录
        Complaint update = new Complaint();
        update.setId(complaintId);
        if (Boolean.TRUE.equals(dto.getCloseAfterReply())) {
            update.setStatus(STATUS_CLOSED);
            update.setHandleTime(LocalDateTime.now());
        } else if (STATUS_PENDING.equals(complaint.getStatus())) {
            update.setStatus(STATUS_PROCESSING);
        }
        if (update.getStatus() != null) {
            updateById(update);
        }
        log.info("工单回复成功：complaintId={}，replyBy={}", complaintId, reply.getReplyBy());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(ComplaintAssignDTO dto) {
        Complaint complaint = requireComplaint(dto.getComplaintId());
        ScenicScope.checkWritable(complaint.getScenicId());
        if (STATUS_CLOSED.equals(complaint.getStatus())) {
            throw new BusinessException("工单已完结，无法转派");
        }
        SysUser handler = sysUserMapper.selectById(dto.getHandlerId());
        if (handler == null) {
            throw new BusinessException("指定的处理人不存在");
        }
        if (handler.getStatus() != null && handler.getStatus() == 0) {
            throw new BusinessException("处理人账号已禁用，请选择其他人员");
        }

        Complaint update = new Complaint();
        update.setId(dto.getComplaintId());
        update.setHandlerId(dto.getHandlerId());
        update.setStatus(STATUS_PROCESSING);
        updateById(update);

        // 转派说明写进回复流，让后续接手的人能看到「为什么转给我」
        if (StringUtils.hasText(dto.getRemark())) {
            ComplaintReply reply = new ComplaintReply();
            reply.setComplaintId(dto.getComplaintId());
            reply.setReplyType("STAFF");
            reply.setContent("【转派】已将工单转交 " + handler.getRealName() + " 处理。说明：" + dto.getRemark());
            reply.setReplyBy(StringUtils.hasText(UserContext.getUsername())
                    ? UserContext.getUsername() : "系统");
            complaintReplyMapper.insert(reply);
        }
        log.info("工单转派成功：complaintId={}，handlerId={}", dto.getComplaintId(), dto.getHandlerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ComplaintVO analyzeSentiment(Long complaintId) {
        Complaint complaint = requireComplaint(complaintId);
        ScenicScope.checkWritable(complaint.getScenicId());

        // 标题往往信息量最高（「排队两小时」「乱收费」），与正文拼在一起分析比只看正文更准
        String text = (complaint.getTitle() == null ? "" : complaint.getTitle())
                + "。"
                + (complaint.getContent() == null ? "" : complaint.getContent());
        double score = aiClient.analyzeSentiment(text);
        String label = sentimentLabel(score);

        Complaint update = new Complaint();
        update.setId(complaintId);
        update.setSentiment(label);
        update.setSentimentScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        update.setSentimentTime(LocalDateTime.now());
        // 负面工单自动升级为紧急，避免情绪激烈的投诉被当成普通工单排在队尾
        if ("NEGATIVE".equals(label) && !"URGENT".equals(complaint.getPriority())) {
            update.setPriority("URGENT");
        }
        updateById(update);

        complaint.setSentiment(label);
        complaint.setSentimentScore(update.getSentimentScore());
        complaint.setSentimentTime(update.getSentimentTime());
        complaint.setPriority(update.getPriority() == null ? complaint.getPriority() : update.getPriority());
        log.info("工单情感分析完成：complaintId={}，score={}，label={}", complaintId, score, label);
        return toVOList(Collections.singletonList(complaint)).get(0);
    }

    @Override
    public ComplaintSummaryVO summarize(ComplaintSummaryDTO dto) {
        Long scopeScenicId = ScenicScope.resolve(null);
        List<Complaint> complaints;
        if (!CollectionUtils.isEmpty(dto == null ? null : dto.getIds())) {
            complaints = this.listByIds(dto.getIds());
        } else {
            // 未指定工单时归纳「待处理 + 处理中」的存量问题，这正是主管每天要看的东西
            complaints = this.list(Wrappers.<Complaint>lambdaQuery()
                    .eq(scopeScenicId != null, Complaint::getScenicId, scopeScenicId)
                    .in(Complaint::getStatus, STATUS_PENDING, STATUS_PROCESSING)
                    .orderByDesc(Complaint::getCreateTime)
                    .last("LIMIT 100"));
        }
        if (complaints.isEmpty()) {
            throw new BusinessException("没有可归纳的工单");
        }

        Map<String, Long> typeCount = new LinkedHashMap<>();
        Map<String, Long> sentimentCount = new LinkedHashMap<>();
        Map<String, Long> keywordCount = new LinkedHashMap<>();
        int negative = 0;
        int urgent = 0;
        int pending = 0;

        for (Complaint complaint : complaints) {
            typeCount.merge(typeText(complaint.getType()), 1L, Long::sum);
            sentimentCount.merge(sentimentText(complaint.getSentiment()), 1L, Long::sum);
            if ("NEGATIVE".equals(complaint.getSentiment())) {
                negative++;
            }
            if ("URGENT".equals(complaint.getPriority())) {
                urgent++;
            }
            if (STATUS_PENDING.equals(complaint.getStatus())) {
                pending++;
            }
            String text = (complaint.getTitle() == null ? "" : complaint.getTitle())
                    + (complaint.getContent() == null ? "" : complaint.getContent());
            for (String keyword : ISSUE_KEYWORDS) {
                if (text.contains(keyword)) {
                    keywordCount.merge(keyword, 1L, Long::sum);
                }
            }
        }

        // 高频问题排序，取前 8 个交给前端做词云或列表
        List<StatItemVO> topKeywords = keywordCount.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(e -> new StatItemVO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        String mainIssue = topKeywords.isEmpty() ? "服务体验" : topKeywords.get(0).getName();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("total", complaints.size());
        context.put("negative", negative);
        context.put("urgent", urgent);
        context.put("mainIssue", mainIssue);
        if (!complaints.isEmpty()) {
            context.put("scenicName", resolveScenicName(complaints.get(0).getScenicId()));
        }

        ComplaintSummaryVO vo = new ComplaintSummaryVO();
        vo.setTotal(complaints.size());
        vo.setNegativeCount(negative);
        vo.setUrgentCount(urgent);
        vo.setPendingCount(pending);
        vo.setTypeDist(toStatItems(typeCount));
        vo.setSentimentDist(toStatItems(sentimentCount));
        vo.setTopKeywords(topKeywords);
        vo.setSummaryText(aiClient.generate("SUMMARY", mainIssue, context));
        vo.setModel(aiClient.modelName());
        return vo;
    }

    /* ==================== 私有方法 ==================== */

    private Complaint requireComplaint(Long id) {
        Complaint complaint = id == null ? null : getById(id);
        if (complaint == null) {
            throw new BusinessException("工单不存在");
        }
        return complaint;
    }

    private String resolveScenicName(Long scenicId) {
        if (scenicId == null) {
            return null;
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        return area == null ? null : area.getScenicName();
    }

    /** 批量查询景区名与处理人姓名，避免逐条回查 */
    private Map<Long, String> nameIndex(List<Complaint> complaints) {
        Map<Long, String> index = new LinkedHashMap<>();
        Set<Long> scenicIds = complaints.stream().map(Complaint::getScenicId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!scenicIds.isEmpty()) {
            scenicAreaMapper.selectBatchIds(scenicIds).forEach(area ->
                    index.put(-area.getId(), area.getScenicName()));
        }
        Set<Long> handlerIds = complaints.stream().map(Complaint::getHandlerId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!handlerIds.isEmpty()) {
            sysUserMapper.selectBatchIds(handlerIds).forEach(user ->
                    index.put(user.getId(), user.getRealName()));
        }
        return index;
    }

    private List<ComplaintVO> toVOList(List<Complaint> complaints) {
        if (complaints == null || complaints.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> index = nameIndex(complaints);
        List<ComplaintVO> list = new ArrayList<>();
        for (Complaint complaint : complaints) {
            ComplaintVO vo = new ComplaintVO();
            fill(vo, complaint, index);
            list.add(vo);
        }
        return list;
    }

    /** 景区名以负数 id 存取，避免与 sys_user 的 id 空间撞车 */
    private void fill(ComplaintVO vo, Complaint complaint, Map<Long, String> index) {
        vo.setId(complaint.getId());
        vo.setTicketNo(complaint.getTicketNo());
        vo.setScenicId(complaint.getScenicId());
        vo.setScenicName(complaint.getScenicId() == null ? null : index.get(-complaint.getScenicId()));
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
        vo.setHandlerName(complaint.getHandlerId() == null ? null : index.get(complaint.getHandlerId()));
        vo.setHandleTime(complaint.getHandleTime());
        vo.setCreateTime(complaint.getCreateTime());
    }

    private ComplaintReplyVO toReplyVO(ComplaintReply reply) {
        ComplaintReplyVO vo = new ComplaintReplyVO();
        vo.setId(reply.getId());
        vo.setComplaintId(reply.getComplaintId());
        vo.setReplyType(reply.getReplyType());
        vo.setReplyTypeText(replyTypeText(reply.getReplyType()));
        vo.setContent(reply.getContent());
        vo.setReplyBy(reply.getReplyBy());
        vo.setSentiment(reply.getSentiment());
        vo.setCreateTime(reply.getCreateTime());
        return vo;
    }

    private List<StatItemVO> toStatItems(Map<String, Long> countMap) {
        List<StatItemVO> items = new ArrayList<>();
        countMap.forEach((name, value) -> items.add(new StatItemVO(name, value)));
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return items;
    }

    /** 情感得分 → 极性标签，与 AI 实现内部保持同一套阈值 */
    private String sentimentLabel(double score) {
        if (score >= 0.2) {
            return "POSITIVE";
        }
        return score <= -0.2 ? "NEGATIVE" : "NEUTRAL";
    }

    private String typeText(String type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case "COMPLAINT" -> "投诉";
            case "SUGGESTION" -> "建议";
            case "CONSULT" -> "咨询";
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

    private String replyTypeText(String replyType) {
        if (replyType == null) {
            return "未知";
        }
        return switch (replyType) {
            case "TOURIST" -> "游客";
            case "STAFF" -> "客服";
            case "AI" -> "智能助手";
            default -> replyType;
        };
    }
}
