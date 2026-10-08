package com.ikun.service;

import java.util.Map;

/**
 * AI 能力抽象接口
 *
 * <p>系统的 AI 能力（情感分析、公告草稿、疏导预案、工单汇总）统一通过本接口调用，
 * 业务代码只依赖抽象，不关心背后是本地规则引擎还是大模型 API。</p>
 *
 * <p><b>为什么先做抽象：</b>本项目当前使用本地规则引擎实现
 * （{@code RuleBasedAiClient}），开箱即可运行、无需申请 API Key，
 * 也不存在外部服务超时拖垮主流程的问题。若后续要接入
 * DeepSeek / 通义千问 / OpenAI 等兼容接口的模型，只需新增一个
 * {@code HttpAiClient} 实现并把配置项 {@code ai.provider} 改为 {@code http}，
 * 业务代码一行都不用改。</p>
 *
 * @author smart-scenic
 */
public interface AiClient {

    /**
     * 当前实现的模型标识，会写入 {@code ai_log}，便于排查「同一功能结果不同」的问题
     */
    String modelName();

    /**
     * 文本生成
     *
     * @param scene   业务场景：ANNOUNCEMENT 公告草稿 / FLOW_PLAN 疏导预案
     *                / COMPLAINT_REPLY 工单回复建议 / SUMMARY 工单归纳
     * @param prompt  用户输入的提示词或主题
     * @param context 附加上下文（如当前人数、承载量、工单内容），可为空
     * @return 生成的文本；生成失败应抛出 {@link com.ikun.common.BusinessException}
     */
    String generate(String scene, String prompt, Map<String, Object> context);

    /**
     * 情感倾向分析
     *
     * @param text 待分析文本
     * @return 分值区间 -1.00（极度负面）~ 1.00（极度正面）
     */
    double analyzeSentiment(String text);
}
