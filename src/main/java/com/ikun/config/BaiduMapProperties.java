package com.ikun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 百度地图开放平台 配置项
 *
 * <p>前端使用浏览器端 {@code ak} 通过 {@code <script>} 加载百度地图 JS API，
 * 后端使用 {@code serverAk} 调用 Web API。
 * 生产环境建议将两个 AK 拆分为不同应用：浏览器端 AK 需配置「Referer 白名单」，
 * 服务端 AK 需配置「IP 白名单」。</p>
 *
 * @author smart-scenic
 */
@Data
@Component
@ConfigurationProperties(prefix = "baidu-map")
public class BaiduMapProperties {

    /** 浏览器端 JS API AK：用于前端 <script src="...ak=..."> 加载 */
    private String ak;

    /** Web 服务 API AK：用于后端地理编码 / 逆地理编码等 */
    private String serverAk;

    /** 默认坐标类型：bd09ll / gcj02 / wgs84 */
    private String coordType;
}