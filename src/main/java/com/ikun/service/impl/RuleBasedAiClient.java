package com.ikun.service.impl;

import com.ikun.service.AiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于规则的本地 AI 实现
 *
 * <p>它不是一个「假的 AI」占位符，而是一个可解释的规则引擎：
 * 情感分析使用情感词典 + 程度副词加权 + 否定词翻转；文本生成使用
 * 场景模板 + 上下文数据填充。优点是不依赖外部服务、响应稳定、
 * 结果可复现（答辩时能逐条解释每个分数是怎么算出来的）。</p>
 *
 * <p>当 {@code ai.provider=http} 时本 Bean 不生效，
 * 由接入真实大模型的实现接管。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedAiClient implements AiClient {

    /* ==================== 情感词典 ==================== */

    /** 正面情感词及其基础权重 */
    private static final Map<String, Double> POSITIVE_WORDS = new LinkedHashMap<>();
    /** 负面情感词及其基础权重 */
    private static final Map<String, Double> NEGATIVE_WORDS = new LinkedHashMap<>();
    /** 程度副词：放大其后情感词的强度 */
    private static final Map<String, Double> DEGREE_WORDS = new LinkedHashMap<>();
    /** 否定词：出现后翻转情感极性 */
    private static final String[] NEGATION_WORDS = {"不", "没", "无", "别", "非", "未"};

    static {
        // 权重 2.0：强烈情绪，往往直接对应投诉
        POSITIVE_WORDS.put("非常感谢", 2.0);
        POSITIVE_WORDS.put("非常满意", 2.0);
        POSITIVE_WORDS.put("点赞", 2.0);
        POSITIVE_WORDS.put("很棒", 1.8);
        POSITIVE_WORDS.put("很满意", 1.8);
        // 权重 1.0：一般正面评价
        POSITIVE_WORDS.put("满意", 1.0);
        POSITIVE_WORDS.put("不错", 1.0);
        POSITIVE_WORDS.put("感谢", 1.0);
        POSITIVE_WORDS.put("谢谢", 1.0);
        POSITIVE_WORDS.put("很好", 1.2);
        POSITIVE_WORDS.put("漂亮", 1.0);
        POSITIVE_WORDS.put("优美", 1.0);
        POSITIVE_WORDS.put("方便", 1.0);
        POSITIVE_WORDS.put("贴心", 1.2);
        POSITIVE_WORDS.put("热情", 1.0);
        POSITIVE_WORDS.put("及时", 1.0);
        POSITIVE_WORDS.put("值得", 1.0);
        POSITIVE_WORDS.put("推荐", 1.0);
        POSITIVE_WORDS.put("干净", 1.0);
        POSITIVE_WORDS.put("舒适", 1.0);
        POSITIVE_WORDS.put("有序", 1.0);

        // 权重 2.0：强烈负面，属于典型投诉诉求
        NEGATIVE_WORDS.put("垃圾", 2.0);
        NEGATIVE_WORDS.put("欺诈", 2.0);
        NEGATIVE_WORDS.put("宰客", 2.0);
        NEGATIVE_WORDS.put("骗人", 2.0);
        NEGATIVE_WORDS.put("太差", 2.0);
        NEGATIVE_WORDS.put("恶心", 2.0);
        // 权重 1.5：明确不满
        NEGATIVE_WORDS.put("投诉", 1.5);
        NEGATIVE_WORDS.put("退款", 1.5);
        NEGATIVE_WORDS.put("乱收费", 1.5);
        NEGATIVE_WORDS.put("失望", 1.5);
        NEGATIVE_WORDS.put("不合理", 1.5);
        NEGATIVE_WORDS.put("态度差", 1.8);
        NEGATIVE_WORDS.put("服务差", 1.8);
        // 权重 1.0：一般负面
        NEGATIVE_WORDS.put("差", 1.0);
        NEGATIVE_WORDS.put("脏", 1.0);
        NEGATIVE_WORDS.put("乱", 1.0);
        NEGATIVE_WORDS.put("慢", 1.0);
        NEGATIVE_WORDS.put("贵", 1.0);
        NEGATIVE_WORDS.put("拥挤", 1.0);
        NEGATIVE_WORDS.put("排队", 1.0);
        NEGATIVE_WORDS.put("难吃", 1.2);
        NEGATIVE_WORDS.put("冷漠", 1.2);
        NEGATIVE_WORDS.put("坑", 1.2);
        NEGATIVE_WORDS.put("臭", 1.2);
        NEGATIVE_WORDS.put("脏乱", 1.5);

        DEGREE_WORDS.put("非常", 1.6);
        DEGREE_WORDS.put("特别", 1.6);
        DEGREE_WORDS.put("十分", 1.5);
        DEGREE_WORDS.put("极其", 1.8);
        DEGREE_WORDS.put("超级", 1.7);
        DEGREE_WORDS.put("太", 1.4);
        DEGREE_WORDS.put("很", 1.3);
        DEGREE_WORDS.put("真", 1.3);
        DEGREE_WORDS.put("有点", 0.6);
        DEGREE_WORDS.put("稍微", 0.6);
    }

    /** 情感分级阈值 */
    private static final double POSITIVE_THRESHOLD = 0.2;
    private static final double NEGATIVE_THRESHOLD = -0.2;

    @Override
    public String modelName() {
        return "rule-engine-v1";
    }

    @Override
    public double analyzeSentiment(String text) {
        if (!StringUtils.hasText(text)) {
            return 0D;
        }
        String content = text.replaceAll("\\s+", "");
        double raw = 0D;

        // 逐词扫描：命中的情感词按「前置程度副词」放大、按「前置否定词」翻转极性
        for (Map.Entry<String, Double> entry : POSITIVE_WORDS.entrySet()) {
            int index = content.indexOf(entry.getKey());
            while (index >= 0) {
                raw += entry.getValue() * modifier(content, index);
                index = content.indexOf(entry.getKey(), index + entry.getKey().length());
            }
        }
        for (Map.Entry<String, Double> entry : NEGATIVE_WORDS.entrySet()) {
            int index = content.indexOf(entry.getKey());
            while (index >= 0) {
                raw -= entry.getValue() * modifier(content, index);
                index = content.indexOf(entry.getKey(), index + entry.getKey().length());
            }
        }

        // tanh 归一化到 (-1, 1)：情感强度随命中词数量增长但不会无限放大，
        // 3 个强负面词就足以逼近 -1，符合人工判断的直觉
        double score = Math.tanh(raw / 3.0);
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 计算情感词前方的修饰系数
     *
     * <p>只看情感词前 3 个字：中文里程度副词与否定词几乎都紧贴在情感词前，
     * 扫描范围放大会误命中前一句里的词。</p>
     */
    private double modifier(String content, int wordIndex) {
        int from = Math.max(0, wordIndex - 3);
        String prefix = content.substring(from, wordIndex);
        double factor = 1D;
        for (Map.Entry<String, Double> degree : DEGREE_WORDS.entrySet()) {
            if (prefix.contains(degree.getKey())) {
                factor *= degree.getValue();
                break;
            }
        }
        for (String negation : NEGATION_WORDS) {
            if (prefix.contains(negation)) {
                // 否定词直接翻转极性，例如「不满意」应判为负面
                factor *= -1;
                break;
            }
        }
        return factor;
    }

    /** 情感分值 → 极性标签 */
    public static String sentimentLabel(double score) {
        if (score >= POSITIVE_THRESHOLD) {
            return "POSITIVE";
        }
        return score <= NEGATIVE_THRESHOLD ? "NEGATIVE" : "NEUTRAL";
    }

    @Override
    public String generate(String scene, String prompt, Map<String, Object> context) {
        Map<String, Object> ctx = context == null ? new LinkedHashMap<>() : context;
        String topic = StringUtils.hasText(prompt) ? prompt.trim() : "景区服务";
        String scenicName = str(ctx.get("scenicName"), "本景区");

        return switch (scene == null ? "" : scene) {
            case "ANNOUNCEMENT" -> announcementDraft(scenicName, topic, ctx);
            case "FLOW_PLAN" -> flowPlan(scenicName, ctx);
            case "COMPLAINT_REPLY" -> complaintReply(scenicName, ctx);
            case "SUMMARY" -> summary(scenicName, ctx);
            default -> scenicName + "关于" + topic + "的服务提示：请游客朋友合理安排行程，"
                    + "如有疑问可随时联系景区客服，我们将竭诚为您服务。";
        };
    }

    /* ==================== 各场景模板 ==================== */

    private String announcementDraft(String scenicName, String topic, Map<String, Object> ctx) {
        String begin = str(ctx.get("beginTime"), "即日起");
        String end = str(ctx.get("endTime"), "另行通知");
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(topic).append("】\n\n");
        sb.append("尊敬的游客朋友：\n\n");
        sb.append("为保障游览安全与体验，").append(scenicName).append("将于")
                .append(begin).append("至").append(end).append("期间对").append(topic)
                .append("相关区域进行调整，具体安排如下：\n\n");
        sb.append("1. 涉及区域实行分时预约，请提前通过官方小程序完成预约后再前往；\n");
        sb.append("2. 现场将增派引导人员，请听从工作人员指挥，按指示标识有序通行；\n");
        sb.append("3. 游览过程中如遇身体不适，请及时联系就近服务点或拨打景区服务热线。\n\n");
        sb.append("给您带来的不便敬请谅解，感谢您的理解与配合！\n\n");
        sb.append(scenicName).append("管理处\n");
        sb.append(java.time.LocalDate.now()).append("\n\n");
        sb.append("（本内容由 AI 生成草稿，发布前请人工核实时间、区域等关键信息）");
        return sb.toString();
    }

    private String flowPlan(String scenicName, Map<String, Object> ctx) {
        int current = num(ctx.get("currentCount"));
        int capacity = num(ctx.get("capacity"));
        String spotName = str(ctx.get("spotName"), scenicName + "核心区域");
        double ratio = capacity <= 0 ? 0 : (double) current / capacity * 100;

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(spotName).append("客流疏导预案】\n\n");
        sb.append("一、当前态势\n");
        sb.append("当前在园 ").append(current).append(" 人，承载量 ").append(capacity)
                .append(" 人，饱和度约 ").append(BigDecimal.valueOf(ratio).setScale(1, RoundingMode.HALF_UP))
                .append("%，已触发")
                .append(ratio >= 90 ? "超载" : ratio >= 70 ? "预警" : "一般")
                .append("级别响应。\n\n");
        sb.append("二、分级响应措施\n");
        if (ratio >= 90) {
            sb.append("1.【立即】暂停该区域门票销售与预约放行，实行「只出不进」；\n");
            sb.append("2.【立即】开启全部备用出入口与应急通道，安排工作人员现场分流；\n");
            sb.append("3.【立即】通过广播、短信、小程序推送提示游客错峰游览；\n");
            sb.append("4.【15 分钟内】上报景区应急指挥部，必要时协调属地公安、医疗支援。\n\n");
        } else if (ratio >= 70) {
            sb.append("1.【立即】启动分时预约限流，下调该区域每时段放行数量；\n");
            sb.append("2.【10 分钟内】在主要入口设置分流引导岗，优先引导团队客转向备用路线；\n");
            sb.append("3.【持续】加密广播提示频次，引导游客前往周边低负荷景点。\n\n");
        } else {
            sb.append("1.【持续】保持现有放行节奏，加密客流监测频次至每 15 分钟一次；\n");
            sb.append("2.【准备】提前布置分流物资与引导标识，做好升级响应准备。\n\n");
        }
        sb.append("三、责任分工\n");
        sb.append("现场总指挥：景区值班经理；客流监测：票务与检票班组；");
        sb.append("游客引导：客服与安保班组；医疗救护：景区医务室。\n\n");
        sb.append("（本内容由 AI 生成草稿，请结合现场实际情况调整后执行）");
        return sb.toString();
    }

    private String complaintReply(String scenicName, Map<String, Object> ctx) {
        String title = str(ctx.get("title"), "您反映的问题");
        String touristName = str(ctx.get("touristName"), "尊敬的游客");
        String type = str(ctx.get("type"), "COMPLAINT");

        String lead = switch (type) {
            case "CONSULT" -> "感谢您的咨询，现就您提出的问题答复如下：";
            case "SUGGESTION" -> "感谢您对景区提出的宝贵建议，我们已认真记录：";
            default -> "非常抱歉给您带来了不佳的游览体验，我们就您反映的问题说明如下：";
        };

        StringBuilder sb = new StringBuilder();
        sb.append(touristName).append("您好：\n\n");
        sb.append(lead).append("\n\n");
        sb.append("关于「").append(title).append("」：经核实，相关情况已反馈至责任部门，")
                .append("我们将在 24 小时内完成整改并向您同步处理结果。");
        if ("COMPLAINT".equals(type)) {
            sb.append("同时我们将举一反三，对同类问题开展专项排查，避免再次发生。");
        }
        sb.append("\n\n如仍有疑问，欢迎随时联系我们，").append(scenicName)
                .append("将竭诚为您服务。再次感谢您的理解与支持！\n\n");
        sb.append("（本内容由 AI 生成建议，回复前请人工确认核实结果）");
        return sb.toString();
    }

    private String summary(String scenicName, Map<String, Object> ctx) {
        int total = num(ctx.get("total"));
        int negative = num(ctx.get("negative"));
        int urgent = num(ctx.get("urgent"));
        String mainIssue = str(ctx.get("mainIssue"), "服务体验");

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(scenicName).append("工单归纳汇总】\n\n");
        sb.append("一、总体情况\n本批次共 ").append(total).append(" 条工单，其中负面情绪 ")
                .append(negative).append(" 条，紧急工单 ").append(urgent).append(" 条。\n\n");
        sb.append("二、集中问题\n游客反馈主要集中在「").append(mainIssue)
                .append("」方面，建议优先安排专项整改。\n\n");
        sb.append("三、处置建议\n");
        sb.append("1. 负面工单由值班主管逐条复核，确保 24 小时内闭环；\n");
        sb.append("2. 紧急工单立即转派至对应责任部门并电话跟进；\n");
        sb.append("3. 将高频问题同步至一线培训与现场标识优化清单。\n\n");
        sb.append("（本内容由 AI 自动归纳，仅供管理参考）");
        return sb.toString();
    }

    private String str(Object value, String defaultValue) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? defaultValue : String.valueOf(value);
    }

    private int num(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
