package com.ikun.service.impl;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ikun.common.BusinessException;
import com.ikun.config.SiliconFlowProperties;
import com.ikun.service.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于「硅基流动 SiliconFlow」开放平台的 AI 客户端实现
 *
 * <p>硅基流动的接口协议与 OpenAI Chat Completions 完全兼容，
 * 免翻墙、有免费档模型（如 Qwen/Qwen2.5-7B-Instruct），适合作为项目 AI 客服的兜底实现。
 * 调用失败（含超时、限流、余额不足等）会向上抛出 {@link BusinessException}，
 * 由全局异常处理器统一收敛为 500 + 中文提示，前端可按需降级到本地规则。</p>
 *
 * <p>启用条件：当 {@code ai.provider == siliconflow} 时本 Bean 生效，
 * 与 {@link RuleBasedAiClient} 二选一。关闭后回落到规则引擎。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "siliconflow")
@RequiredArgsConstructor
public class SiliconFlowClient implements AiClient {

    /** SiliconFlow / OpenAI 兼容接口 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final SiliconFlowProperties props;

    @Override
    public String modelName() {
        return props.getChatModel();
    }

    @Override
    public String generate(String scene, String prompt, Map<String, Object> context) {
        Map<String, Object> ctx = context == null ? new LinkedHashMap<>() : context;
        String systemPrompt = buildSystemPrompt(scene, ctx);
        String userPrompt = buildUserPrompt(scene, prompt, ctx);
        return callChatCompletion(systemPrompt, userPrompt);
    }

    @Override
    public double analyzeSentiment(String text) {
        if (!StringUtils.hasText(text)) {
            return 0D;
        }
        // 让模型直接返回 -1 ~ 1 的小数；只取第一行，避免模型输出多余说明
        String system = "你是景区工单情感分析助手。请阅读游客反馈，仅输出一个 -1 到 1 之间的数字，" +
                "保留两位小数。负数表示负面，正数表示绝对正，0 表示中性。只输出数字，不要任何其他字符。";
        String answer;
        try {
            answer = callChatCompletion(system, text.trim());
        } catch (Exception e) {
            log.warn("硅基流动情感分析失败，回退到规则引擎：{}", e.getMessage());
            // 模型调用失败时退回到本地规则，保证工单流程不被拖垮
            return new RuleBasedAiClient().analyzeSentiment(text);
        }
        try {
            // 提取数字：容忍「-0.85」/「-0.85 分」等混合输出
            String first = answer.trim().split("\\s+")[0];
            double score = Double.parseDouble(first);
            // 钳制到 [-1, 1]
            return Math.max(-1D, Math.min(1D, score));
        } catch (Exception e) {
            log.warn("情感分析返回非数字：{}，回退规则", answer);
            return new RuleBasedAiClient().analyzeSentiment(text);
        }
    }

    /* ============================================================
     *                      与硅基流动交互
     * ============================================================ */

    /**
     * 实际调用硅基流动 /chat/completions 接口
     *
     * @param system 系统提示词（角色 / 输出约束）
     * @param user   业务输入
     * @return 模型生成文本
     * @throws BusinessException 当鉴权失败 / 余额不足 / 网络异常 / 模型超时 时抛出
     */
    private String callChatCompletion(String system, String user) {
        if (!StringUtils.hasText(props.getApiKey())) {
            throw new BusinessException("硅基流动 api-key 未配置（siliconflow.api-key）");
        }
        if (!StringUtils.hasText(props.getBaseUrl())) {
            throw new BusinessException("硅基流动 base-url 未配置（siliconflow.base-url）");
        }

        JSONObject body = new JSONObject();
        body.set("model", props.getChatModel());
        body.set("max_tokens", props.getMaxTokens() == null ? 1024 : props.getMaxTokens());
        body.set("temperature", 0.4);
        body.set("stream", false);
        body.set("messages", new JSONArray(List.of(
                new JSONObject().set("role", "system").set("content", system),
                new JSONObject().set("role", "user").set("content", user)
        )));

        String url = props.getBaseUrl().replaceAll("/+$", "") + CHAT_COMPLETIONS_PATH;
        int timeoutMs = props.getTimeout() == null ? 30000 : props.getTimeout().intValue();

        try (HttpResponse response = HttpRequest.post(url)
                .header(Header.AUTHORIZATION, "Bearer " + props.getApiKey())
                .header(Header.CONTENT_TYPE, "application/json; charset=utf-8")
                .timeout(timeoutMs)
                .body(body.toString())
                .execute()) {

            if (!response.isOk()) {
                String err = response.body();
                log.error("硅基流动调用失败：status={} body={}", response.getStatus(), err);
                throw new BusinessException("AI 客服调用失败：" + response.getStatus());
            }
            JSONObject json = JSONUtil.parseObj(response.body());
            JSONArray choices = json.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 客服响应缺少 choices 字段");
            }
            Object content = choices.getJSONObject(0)
                    .getJSONObject("message")
                    .get("content");
            if (content == null) {
                throw new BusinessException("AI 客服响应缺少 message.content");
            }
            return String.valueOf(content).trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("硅基流动调用异常：{}", e.getMessage(), e);
            throw new BusinessException("AI 客服调用异常：" + e.getMessage());
        }
    }

    /* ============================================================
     *                      Prompt 构造
     * ============================================================ */

    /**
     * 针对不同业务场景拼装系统提示词，约束模型输出形态
     */
    private String buildSystemPrompt(String scene, Map<String, Object> ctx) {
        String scenicName = str(ctx.get("scenicName"), "本景区");
        return switch (scene == null ? "" : scene) {
            case "ANNOUNCEMENT" -> "你是景区文案助理。请基于用户主题撰写一篇正式、得体、可对外发布的景区公告，" +
                    "当前景区：" + scenicName + "。请输出中文 Markdown 文本，正文 300 字以内。";
            case "FLOW_PLAN" -> "你是景区客流调度专家。请基于给出的实时人数 / 承载量数据，" +
                    "输出一个分级响应方案，包含【态势、措施、岗位】三段，景区：" + scenicName + "。中文 Markdown 输出。";
            case "COMPLAINT_REPLY" -> "你是景区客服。请基于工单信息撰写一段礼貌、专业的回复草稿，" +
                    "景区：" + scenicName + "。要求先共情再给处理建议，结尾引导游客继续联系。中文 Markdown。";
            case "SUMMARY" -> "你是景区运营分析师。请把工单列表按类型与紧急程度归纳成摘要，" +
                    "景区：" + scenicName + "。中文 Markdown，三段：总体情况、集中问题、处置建议。";
            case "KNOWLEDGE" -> "你是景区智能客服「智景」。回答景区游客咨询时务必简洁友好，" +
                    "涉及票务 / 路线 / 服务 / 安全 / 退票等硬信息以景区官方公布为准。" +
                    "不知道的内容请直接说明不知道，不要编造。当前景区：" + scenicName + "。";
            default -> "你是景区智能客服「智景」，请用中文简短回答用户问题，景区：" + scenicName + "。";
        };
    }

    /**
     * 业务输入：把上下文数据拼成自然语言，让模型更容易"读懂"
     */
    private String buildUserPrompt(String scene, String prompt, Map<String, Object> ctx) {
        String topic = StringUtils.hasText(prompt) ? prompt.trim() : "无具体主题";
        StringBuilder sb = new StringBuilder();
        sb.append("主题：").append(topic).append('\n');
        if (ctx.containsKey("spotName")) {
            sb.append("景点：").append(ctx.get("spotName")).append('\n');
        }
        if (ctx.containsKey("currentCount") && ctx.containsKey("capacity")) {
            sb.append("当前在园 ").append(ctx.get("currentCount"))
                    .append(" 人，承载量 ").append(ctx.get("capacity")).append(" 人。\n");
        }
        if (ctx.containsKey("beginTime") && ctx.containsKey("endTime")) {
            sb.append("时段：").append(ctx.get("beginTime")).append(" ~ ").append(ctx.get("endTime")).append("。\n");
        }
        if (ctx.containsKey("total")) {
            sb.append("工单总数 ").append(ctx.get("total"))
                    .append("，负面 ").append(ctx.getOrDefault("negative", 0))
                    .append("，紧急 ").append(ctx.getOrDefault("urgent", 0)).append("。\n");
        }
        return sb.toString().trim();
    }

    private String str(Object v, String def) {
        return v == null || !StringUtils.hasText(String.valueOf(v)) ? def : String.valueOf(v);
    }
}