package com.ikun.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云人脸识别（IAI）配置项
 *
 * <p>SecretId / SecretKey 在腾讯云控制台「访问管理 - API 密钥管理」中创建；
 * 人脸库 GroupId 固定为 {@code smart-scenic}，服务启动后按需自动创建。</p>
 *
 * @author smart-scenic
 */
@Data
@Component
@ConfigurationProperties(prefix = "tencent.face")
public class TencentFaceProperties {

    /** 腾讯云 SecretId（AKID 开头） */
    private String secretId;

    /** 腾讯云 SecretKey，与 SecretId 成对出现 */
    private String secretKey;

    /** 服务地域，人脸识别 IAI 常用 ap-guangzhou */
    private String region = "ap-guangzhou";

    /** 人脸库 GroupId */
    private String groupId = "smart-scenic";

    /** 人脸库展示名称 */
    private String groupName = "智慧景区人脸库";

    /** 1:N 搜索最低相似度阈值（0~100） */
    private Double matchThreshold = 70.0;

    /** 配置是否完整（SecretId 与 SecretKey 都非空） */
    public boolean isConfigured() {
        return secretId != null && !secretId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
