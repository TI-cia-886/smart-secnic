package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.common.AppUserContext;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.common.ScenicScope;
import com.ikun.common.UserContext;
import com.ikun.dto.AiChatDTO;
import com.ikun.dto.AiGenerateDTO;
import com.ikun.entity.AiChat;
import com.ikun.entity.AiKnowledge;
import com.ikun.entity.AiLog;
import com.ikun.entity.FlowWarning;
import com.ikun.entity.PassengerFlow;
import com.ikun.entity.ScenicArea;
import com.ikun.entity.ScenicSpot;
import com.ikun.mapper.AiChatMapper;
import com.ikun.mapper.AiKnowledgeMapper;
import com.ikun.mapper.AiLogMapper;
import com.ikun.mapper.FlowWarningMapper;
import com.ikun.mapper.PassengerFlowMapper;
import com.ikun.mapper.ScenicAreaMapper;
import com.ikun.mapper.ScenicSpotMapper;
import com.ikun.service.AiClient;
import com.ikun.service.AiService;
import com.ikun.vo.AiChatReplyVO;
import com.ikun.vo.AiOverviewVO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AI 智能管理服务实现
 *
 * <p>问答采用「知识库优先 + 模型兜底」两段式：先做知识库检索，
 * 命中就返回人工审核过的标准答案，命中不了才交给模型生成。
 * 这样既保证了票价、开放时间这类硬信息的准确性，又保留了应对长尾问题的能力。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 知识库命中阈值：低于该分数视为未命中，交由模型生成 */
    private static final double MATCH_THRESHOLD = 0.25;

    private static final String LOG_SUCCESS = "SUCCESS";
    private static final String LOG_FAIL = "FAIL";

    private final AiKnowledgeMapper aiKnowledgeMapper;
    private final AiLogMapper aiLogMapper;
    private final AiChatMapper aiChatMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final ScenicSpotMapper scenicSpotMapper;
    private final FlowWarningMapper flowWarningMapper;
    private final PassengerFlowMapper passengerFlowMapper;
    private final AiClient aiClient;

    /* ==================== 知识库维护 ==================== */

    @Override
    public PageResult<AiKnowledge> pageKnowledge(Integer pageNum, Integer pageSize, String keyword,
                                                 Long scenicId, String category, Integer status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        LambdaQueryWrapper<AiKnowledge> wrapper = Wrappers.<AiKnowledge>lambdaQuery()
                // 通用知识（scenicId 为 NULL）对所有景区都适用，不能简单 eq 掉
                .and(scopeScenicId != null, w -> w
                        .eq(AiKnowledge::getScenicId, scopeScenicId).or()
                        .isNull(AiKnowledge::getScenicId))
                .and(StringUtils.hasText(keyword), w -> w
                        .like(AiKnowledge::getQuestion, keyword).or()
                        .like(AiKnowledge::getKeywords, keyword))
                .eq(StringUtils.hasText(category), AiKnowledge::getCategory, category)
                .eq(status != null, AiKnowledge::getStatus, status)
                .orderByDesc(AiKnowledge::getHitCount)
                .orderByDesc(AiKnowledge::getId);
        return PageResult.of(aiKnowledgeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveKnowledge(AiKnowledge knowledge) {
        knowledge.setId(null);
        knowledge.setCreateTime(null);
        knowledge.setUpdateTime(null);
        knowledge.setHitCount(0);
        // 向量字段由离线任务回填，不接受接口传入，避免写入与实际模型不一致的脏向量
        knowledge.setEmbedding(null);
        knowledge.setEmbeddingModel(null);

        knowledge.setScenicId(ScenicScope.fillWritable(knowledge.getScenicId()));
        validateKnowledge(knowledge);
        assertQuestionUnique(knowledge.getQuestion(), knowledge.getScenicId(), null);
        if (knowledge.getStatus() == null) {
            knowledge.setStatus(1);
        }
        aiKnowledgeMapper.insert(knowledge);
        log.info("新增知识库条目：id={}，question={}", knowledge.getId(), knowledge.getQuestion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AiKnowledge knowledge) {
        if (knowledge.getId() == null) {
            throw new BusinessException("知识条目ID不能为空");
        }
        AiKnowledge exist = aiKnowledgeMapper.selectById(knowledge.getId());
        if (exist == null) {
            throw new BusinessException("知识条目不存在");
        }
        ScenicScope.checkWritable(exist.getScenicId());

        knowledge.setScenicId(exist.getScenicId());
        knowledge.setCreateTime(null);
        knowledge.setUpdateTime(null);
        knowledge.setHitCount(null);
        knowledge.setEmbedding(null);
        knowledge.setEmbeddingModel(null);
        validateKnowledge(knowledge);
        assertQuestionUnique(knowledge.getQuestion(), exist.getScenicId(), knowledge.getId());

        // 问答内容变了，旧向量就失效了，一并清掉等待重新向量化
        if (StringUtils.hasText(knowledge.getQuestion())
                && !knowledge.getQuestion().equals(exist.getQuestion())) {
            aiKnowledgeMapper.update(null, Wrappers.<AiKnowledge>lambdaUpdate()
                    .eq(AiKnowledge::getId, knowledge.getId())
                    .set(AiKnowledge::getEmbedding, null)
                    .set(AiKnowledge::getEmbeddingModel, null));
        }
        aiKnowledgeMapper.updateById(knowledge);
        log.info("编辑知识库条目：id={}", knowledge.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeKnowledge(Long id) {
        AiKnowledge exist = aiKnowledgeMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("知识条目不存在");
        }
        ScenicScope.checkWritable(exist.getScenicId());
        aiKnowledgeMapper.deleteById(id);
        log.info("删除知识库条目：id={}", id);
    }

    /* ==================== 日志与概览 ==================== */

    @Override
    public PageResult<AiLog> pageLog(Integer pageNum, Integer pageSize, Long scenicId,
                                     String module, String status) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<AiLog>lambdaQuery()
                .eq(scopeScenicId != null, AiLog::getScenicId, scopeScenicId)
                .eq(StringUtils.hasText(module), AiLog::getModule, module)
                .eq(StringUtils.hasText(status), AiLog::getStatus, status)
                .orderByDesc(AiLog::getCreateTime);
        return PageResult.of(aiLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public PageResult<AiChat> pageChat(Integer pageNum, Integer pageSize, Long scenicId,
                                       String sessionId, Long touristId) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);
        var wrapper = Wrappers.<AiChat>lambdaQuery()
                .eq(scopeScenicId != null, AiChat::getScenicId, scopeScenicId)
                .eq(StringUtils.hasText(sessionId), AiChat::getSessionId, sessionId)
                .eq(touristId != null, AiChat::getTouristId, touristId)
                .orderByDesc(AiChat::getCreateTime);
        return PageResult.of(aiChatMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public AiOverviewVO overview(Long scenicId) {
        Long scopeScenicId = ScenicScope.resolve(scenicId);

        List<AiLog> logs = aiLogMapper.selectList(Wrappers.<AiLog>lambdaQuery()
                .eq(scopeScenicId != null, AiLog::getScenicId, scopeScenicId));
        AiOverviewVO vo = new AiOverviewVO();
        vo.setModel(aiClient.modelName());

        long success = 0;
        long fail = 0;
        long durationSum = 0;
        long durationCount = 0;
        long todayCalls = 0;
        String today = LocalDate.now().format(DATE_FORMAT);
        Map<String, Long> moduleCount = new LinkedHashMap<>();
        Map<String, Long> dailyCount = new LinkedHashMap<>();

        for (AiLog aiLog : logs) {
            if (LOG_SUCCESS.equals(aiLog.getStatus())) {
                success++;
            } else {
                fail++;
            }
            if (aiLog.getDuration() != null) {
                durationSum += aiLog.getDuration();
                durationCount++;
            }
            if (aiLog.getCreateTime() != null) {
                String day = aiLog.getCreateTime().toLocalDate().format(DATE_FORMAT);
                dailyCount.merge(day, 1L, Long::sum);
                if (today.equals(day)) {
                    todayCalls++;
                }
            }
            moduleCount.merge(moduleText(aiLog.getModule()), 1L, Long::sum);
        }

        vo.setTotalCalls((long) logs.size());
        vo.setSuccessCalls(success);
        vo.setFailCalls(fail);
        vo.setSuccessRate(rate(success, logs.size()));
        vo.setAvgDuration(durationCount == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(durationSum)
                .divide(BigDecimal.valueOf(durationCount), 1, RoundingMode.HALF_UP));
        vo.setTodayCalls(todayCalls);

        List<AiKnowledge> knowledgeList = aiKnowledgeMapper.selectList(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(scopeScenicId != null, AiKnowledge::getScenicId, scopeScenicId));
        vo.setKnowledgeCount((long) knowledgeList.size());
        vo.setEnabledKnowledgeCount(knowledgeList.stream()
                .filter(k -> k.getStatus() != null && k.getStatus() == 1).count());

        Long questionCount = aiChatMapper.selectCount(Wrappers.<AiChat>lambdaQuery()
                .eq(scopeScenicId != null, AiChat::getScenicId, scopeScenicId)
                .eq(AiChat::getRole, "user"));
        vo.setChatCount(aiChatMapper.selectCount(Wrappers.<AiChat>lambdaQuery()
                .eq(scopeScenicId != null, AiChat::getScenicId, scopeScenicId)));
        // 知识库命中率 = 累计命中次数 / 累计提问次数，直接反映知识库覆盖得够不够
        long hitTotal = knowledgeList.stream()
                .mapToLong(k -> k.getHitCount() == null ? 0L : k.getHitCount()).sum();
        vo.setKnowledgeHitRate(rate(hitTotal, questionCount == null ? 0L : questionCount));

        List<StatItemVO> moduleItems = new ArrayList<>();
        moduleCount.forEach((name, value) -> moduleItems.add(new StatItemVO(name, value)));
        moduleItems.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        vo.setModuleDist(moduleItems);

        List<TrendItemVO> trend = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(6);
        for (LocalDate date = start; !date.isAfter(LocalDate.now()); date = date.plusDays(1)) {
            String key = date.format(DATE_FORMAT);
            trend.add(new TrendItemVO(key, dailyCount.getOrDefault(key, 0L)));
        }
        vo.setCallTrend(trend);
        return vo;
    }

    /* ==================== 问答与生成 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiChatReplyVO chat(AiChatDTO dto) {
        long start = System.currentTimeMillis();
        Long scenicId = ScenicScope.resolve(dto.getScenicId());
        String sessionId = StringUtils.hasText(dto.getSessionId())
                ? dto.getSessionId()
                : UUID.randomUUID().toString().replace("-", "");

        // 先落用户消息再回答：即使后续生成失败，对话记录里也能看到游客问了什么
        AiChat userMessage = new AiChat();
        userMessage.setSessionId(sessionId);
        userMessage.setScenicId(scenicId);
        // 小程序端调问答时会带上游客身份；后台客服调试场景下为 null，不影响记录
        userMessage.setTouristId(AppUserContext.getTouristId());
        userMessage.setRole("user");
        userMessage.setContent(dto.getQuestion());
        aiChatMapper.insert(userMessage);

        AiKnowledge matched = matchKnowledge(dto.getQuestion(), scenicId);
        String answer;
        String source;
        if (matched != null) {
            answer = matched.getAnswer();
            source = "KNOWLEDGE";
            // 命中次数是知识库运营的核心指标：长期零命中的条目说明关键词没配好
            aiKnowledgeMapper.update(null, Wrappers.<AiKnowledge>lambdaUpdate()
                    .eq(AiKnowledge::getId, matched.getId())
                    .setSql("hit_count = hit_count + 1"));
        } else {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("scenicName", resolveScenicName(scenicId));
            answer = aiClient.generate("KNOWLEDGE", dto.getQuestion(), context);
            source = "GENERATED";
        }

        AiChat assistantMessage = new AiChat();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setScenicId(scenicId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        aiChatMapper.insert(assistantMessage);

        int duration = (int) (System.currentTimeMillis() - start);
        writeLog("KNOWLEDGE", dto.getQuestion(),
                "命中知识库：" + (matched == null ? "否，由模型生成" : matched.getQuestion()),
                duration, LOG_SUCCESS, null, scenicId);

        AiChatReplyVO vo = new AiChatReplyVO();
        vo.setSessionId(sessionId);
        vo.setAnswer(answer);
        vo.setSource(source);
        vo.setKnowledgeId(matched == null ? null : matched.getId());
        vo.setMatchedQuestion(matched == null ? null : matched.getQuestion());
        vo.setDuration(duration);
        vo.setModel(aiClient.modelName());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generate(AiGenerateDTO dto) {
        long start = System.currentTimeMillis();
        Long scenicId = ScenicScope.resolve(dto.getScenicId());
        String scene = dto.getScene().trim().toUpperCase();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("scenicName", resolveScenicName(scenicId));
        context.put("beginTime", dto.getBeginTime());
        context.put("endTime", dto.getEndTime());

        FlowWarning warning = null;
        if (dto.getWarningId() != null) {
            warning = flowWarningMapper.selectById(dto.getWarningId());
            if (warning == null) {
                throw new BusinessException("预警记录不存在");
            }
            scenicId = warning.getScenicId() == null ? scenicId : warning.getScenicId();
            context.put("spotName", warning.getSpotName());
            context.put("currentCount", warning.getCurrentCount());
            context.put("capacity", warning.getCapacity());
        } else if (dto.getSpotId() != null) {
            ScenicSpot spot = scenicSpotMapper.selectById(dto.getSpotId());
            if (spot == null) {
                throw new BusinessException("景点不存在");
            }
            context.put("spotName", spot.getSpotName());
            context.put("currentCount", spot.getCurrentCount());
            context.put("capacity", spot.getInstantCapacity());
        } else if ("FLOW_PLAN".equals(scene)) {
            // 未指定景点时用「景区当前在园人数 + 日承载量」兜底，预案才不至于没有数字可依据
            fillScenicFlowContext(context, scenicId);
        }

        try {
            String text = aiClient.generate(scene, dto.getPrompt(), context);
            writeLog(scene, StringUtils.hasText(dto.getPrompt()) ? dto.getPrompt() : scene,
                    text, (int) (System.currentTimeMillis() - start), LOG_SUCCESS, null, scenicId);

            // 基于预警生成的预案直接回写到预警记录，处置人打开页面就能看到现成方案
            if (warning != null && "FLOW_PLAN".equals(scene)) {
                FlowWarning update = new FlowWarning();
                update.setId(warning.getId());
                update.setAiPlan(text);
                update.setAiPlanTime(LocalDateTime.now());
                flowWarningMapper.updateById(update);
            }
            return text;
        } catch (Exception e) {
            writeLog(scene, StringUtils.hasText(dto.getPrompt()) ? dto.getPrompt() : scene,
                    null, (int) (System.currentTimeMillis() - start), LOG_FAIL, e.getMessage(), scenicId);
            log.error("AI 生成失败：scene={}", scene, e);
            throw new BusinessException("AI 生成失败：" + e.getMessage());
        }
    }

    /* ==================== 私有方法 ==================== */

    /** 用景区当日的实时数据补全上下文 */
    private void fillScenicFlowContext(Map<String, Object> context, Long scenicId) {
        if (scenicId == null) {
            return;
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        if (area != null) {
            context.put("scenicName", area.getScenicName());
            context.put("capacity", area.getDailyCapacity());
            context.put("spotName", area.getScenicName() + "全域");
        }
        PassengerFlow latest = passengerFlowMapper.selectOne(Wrappers.<PassengerFlow>lambdaQuery()
                .eq(PassengerFlow::getScenicId, scenicId)
                .eq(PassengerFlow::getStatDate, LocalDate.now())
                .orderByDesc(PassengerFlow::getStatHour)
                .last("LIMIT 1"));
        context.put("currentCount", latest == null ? 0 : latest.getCurrentCount());
    }

    /**
     * 知识库检索
     *
     * <p>评分 = 人工关键词命中率 × 0.6 + 问句字符重合度 × 0.4。
     * 给关键词更高权重，是因为关键词是运营人员针对本景区专门配置的，
     * 比通用的字符重合度更贴近真实业务语义。</p>
     */
    private AiKnowledge matchKnowledge(String question, Long scenicId) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        List<AiKnowledge> candidates = aiKnowledgeMapper.selectList(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(AiKnowledge::getStatus, 1)
                .and(w -> w.eq(scenicId != null, AiKnowledge::getScenicId, scenicId).or()
                        .isNull(AiKnowledge::getScenicId)));
        if (candidates.isEmpty()) {
            return null;
        }

        Set<Character> questionChars = charSet(question);
        AiKnowledge best = null;
        double bestScore = 0D;
        for (AiKnowledge candidate : candidates) {
            double score = 0D;
            if (StringUtils.hasText(candidate.getKeywords())) {
                String[] keywords = candidate.getKeywords().split("[,，、\\s]+");
                int hit = 0;
                int valid = 0;
                for (String keyword : keywords) {
                    if (!StringUtils.hasText(keyword)) {
                        continue;
                    }
                    valid++;
                    if (question.contains(keyword.trim())) {
                        hit++;
                    }
                }
                if (valid > 0) {
                    score += 0.6 * hit / valid;
                }
            }
            if (StringUtils.hasText(candidate.getQuestion())) {
                score += 0.4 * jaccard(questionChars, charSet(candidate.getQuestion()));
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null && bestScore >= MATCH_THRESHOLD) {
            log.debug("知识库命中：question={}，matched={}，score={}", question, best.getQuestion(), bestScore);
            return best;
        }
        return null;
    }

    private Set<Character> charSet(String text) {
        Set<Character> set = new HashSet<>();
        if (text == null) {
            return set;
        }
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                set.add(c);
            }
        }
        return set;
    }

    /** Jaccard 相似度：交集 / 并集，对中文短句的容错度尚可且无需分词库 */
    private double jaccard(Set<Character> a, Set<Character> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0D;
        }
        Set<Character> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Character> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private void validateKnowledge(AiKnowledge knowledge) {
        if (!StringUtils.hasText(knowledge.getQuestion())) {
            throw new BusinessException("标准问题不能为空");
        }
        if (!StringUtils.hasText(knowledge.getAnswer())) {
            throw new BusinessException("标准答案不能为空");
        }
    }

    private void assertQuestionUnique(String question, Long scenicId, Long excludeId) {
        if (!StringUtils.hasText(question)) {
            return;
        }
        Long count = aiKnowledgeMapper.selectCount(Wrappers.<AiKnowledge>lambdaQuery()
                .eq(AiKnowledge::getQuestion, question)
                .eq(scenicId != null, AiKnowledge::getScenicId, scenicId)
                .isNull(scenicId == null, AiKnowledge::getScenicId)
                .ne(excludeId != null, AiKnowledge::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("已存在相同的标准问题：" + question);
        }
    }

    private String resolveScenicName(Long scenicId) {
        if (scenicId == null) {
            return "本景区";
        }
        ScenicArea area = scenicAreaMapper.selectById(scenicId);
        return area == null ? "本景区" : area.getScenicName();
    }

    /** 记录 AI 调用日志；日志写失败不能影响主流程，因此单独兜底 */
    private void writeLog(String module, String input, String output, Integer duration,
                          String status, String errorMsg, Long scenicId) {
        try {
            AiLog aiLog = new AiLog();
            aiLog.setScenicId(scenicId);
            aiLog.setModule(module);
            aiLog.setInputSummary(truncate(input, 500));
            aiLog.setOutputSummary(truncate(output, 500));
            aiLog.setDuration(duration);
            aiLog.setStatus(status);
            aiLog.setErrorMsg(truncate(errorMsg, 500));
            aiLog.setOperatorId(UserContext.getUserId());
            aiLogMapper.insert(aiLog);
        } catch (Exception e) {
            log.warn("写入 AI 调用日志失败：{}", e.getMessage());
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String moduleText(String module) {
        if (module == null) {
            return "其他";
        }
        return switch (module) {
            case "KNOWLEDGE" -> "智能问答";
            case "SENTIMENT" -> "情感分析";
            case "ANNOUNCEMENT" -> "公告草稿";
            case "SUMMARY" -> "工单归纳";
            case "FLOW_PLAN" -> "疏导预案";
            case "ROUTE" -> "路线推荐";
            default -> module;
        };
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
