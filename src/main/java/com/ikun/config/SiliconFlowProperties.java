package com.ikun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 硅基流动（SiliconFlow）开放平台 配置项
 *
 * <p>硅基流动提供与 OpenAI Chat Completions 完全兼容的 HTTP 接口，
 * 免费档智能体（如 Qwen2.5-7B-Instruct）免申请、即取即用，
 * 适合作为项目 AI 客服的兜底实现。</p>
 *
 * <p>启用方式：在 {@code application-dev.yml} 中新增
 * {@code ai.provider: siliconflow} 即可让 {@link com.ikun.service.impl.SiliconFlowClient}
 * 接管 {@link com.ikun.service.AiClient} 的 Bean，
 * 业务侧 {@link com.ikun.service.AiService} 无需改动一行。</p>
 *
 * @author smart-scenic
 */
@Data
@Component
@ConfigurationProperties(prefix = "siliconflow")
public class SiliconFlowProperties {

    /** 鉴权 API Key（Bearer Token） */
    private String apiKey;

    /** 基础地址，例如 https://api.siliconflow.cn/v1 */
    private String baseUrl;

    /** 对话模型 ID，例如 Qwen/Qwen2.5-7B-Instruct */
    private String chatModel;

    /** 单次调用超时（毫秒） */
    private Long timeout;

    /** 最大输出 token 数 */
    private Integer maxTokens;
}