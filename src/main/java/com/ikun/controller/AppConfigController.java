package com.ikun.controller;

import com.ikun.common.Result;
import com.ikun.config.BaiduMapProperties;
import com.ikun.config.SiliconFlowProperties;
import com.ikun.service.AiClient;
import com.ikun.vo.MapConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第三方服务配置接口（公开接口，前端启动时拉取一次即可）
 *
 * <p>设计上对外暴露的字段都「不算敏感」：
 * <ul>
 *   <li>百度地图浏览器端 AK 是公网标识，由百度开放平台侧的「Referer 白名单」保护；</li>
 *   <li>服务端 AK 永不暴露给前端；</li>
 *   <li>硅基流动 API Key 仅保留在服务端配置，前端只看到模型名和供应商。</li>
 * </ul>
 * </p>
 *
 * @author smart-scenic
 */
@Tag(name = "15-第三方服务配置", description = "前端启动时拉取的运行期配置（百度地图 AK / AI 提供方）")
@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class AppConfigController {

    private final BaiduMapProperties baiduMapProperties;
    private final SiliconFlowProperties siliconFlowProperties;

    /**
     * 当前激活的 AI 客户端。
     * <p>{@link ObjectProvider#getIfAvailable} 会按 <code>@ConditionalOnProperty</code>
     * 的结果挑选唯一激活的 Bean，未配置时（本地规则引擎）也不至于让 Controller 启动失败。</p>
     */
    private final ObjectProvider<AiClient> aiClientProvider;

    /** AI 提供方标识，未设置时默认为 rule */
    @Value("${ai.provider:rule}")
    private String aiProvider;

    /**
     * 第三方服务配置（百度地图 + AI）
     *
     * <p>前端可在 App.vue / main.js 启动时拉一次，缓存到 Pinia / sessionStorage，
     * 后续用到地图或 AI 客服时直接读取。</p>
     */
    @Operation(summary = "获取第三方服务配置", description = "前端启动时拉取：百度地图 AK、默认坐标类型、AI 模型标识")
    @GetMapping("/public")
    public Result<MapConfigVO> publicConfig() {
        MapConfigVO vo = new MapConfigVO();
        vo.setBaiduMapAk(baiduMapProperties.getAk());
        vo.setCoordType(baiduMapProperties.getCoordType());
        vo.setAiProvider(aiProvider);
        // 反映当前激活的 AI 客户端是规则引擎还是大模型
        AiClient client = aiClientProvider.getIfAvailable();
        vo.setAiModel(client == null ? "rule-engine-v1" : client.modelName());
        return Result.success(vo);
    }
}