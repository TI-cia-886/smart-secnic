package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 第三方服务配置（百度地图 / 硅基流动）对外暴露 VO
 *
 * <p>仅暴露「前端必需」且「不敏感」的配置项：
 * 浏览器端 AK 默认公网可见（前端 <script> 加载就需要），由百度地图开放平台侧做 Referer 白名单保护。
 * 服务端 AK 不对外暴露，避免被恶意盗刷。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "第三方服务配置")
public class MapConfigVO {

    @Schema(description = "百度地图浏览器端 AK，用于前端 <script src=...> 加载")
    private String baiduMapAk;

    @Schema(description = "百度地图默认坐标类型：bd09ll / gcj02 / wgs84")
    private String coordType;

    @Schema(description = "AI 提供方标识：rule（本地规则） / siliconflow（硅基流动大模型）")
    private String aiProvider;

    @Schema(description = "当前生效的模型名（用于前端调试展示）")
    private String aiModel;
}