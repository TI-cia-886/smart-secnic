package com.ikun.util;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 对称加解密工具（用于手机号等"需要回显"的敏感字段）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>口令经 SHA-256 派生出 32 字节（256 bit）密钥，避免密钥长度不足；</li>
 *   <li>每次加密生成 12 字节随机 IV，相同明文也会产生不同密文，防止密文比对破解；</li>
 *   <li>GCM 模式自带完整性校验（MAC），密文被篡改时解密会直接失败；</li>
 *   <li>存储格式：{@code Base64( IV(12B) + 密文 + 认证标签(16B) )}。</li>
 * </ul>
 *
 * <p>密钥保存在静态字段中，因为 MyBatis 的 TypeHandler 由 MyBatis 反射实例化、
 * 不经 Spring 容器，无法直接使用 {@code @Value} 注入。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Component
public class AesUtil {

    /** GCM 变换算法 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    /** GCM 推荐的 IV 长度：96 bit */
    private static final int IV_LENGTH = 12;
    /** 认证标签长度（bit） */
    private static final int TAG_LENGTH = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** AES-256 密钥，由构造器在 Spring 启动时初始化 */
    private static SecretKeySpec secretKey;

    public AesUtil(@Value("${security.aes.secret:}") String secret) {
        AesUtil.secretKey = deriveKey(secret);
        log.info("敏感数据加密已启用：AES-256-GCM（密钥长度 {} bit）", AesUtil.secretKey.getEncoded().length * 8);
    }

    /** 任意长度口令 → SHA-256 → 32 字节密钥 */
    private static SecretKeySpec deriveKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("未配置 security.aes.secret，无法启用敏感数据加密");
        }
        return new SecretKeySpec(DigestUtil.sha256(secret.getBytes(StandardCharsets.UTF_8)), ALGORITHM);
    }

    /**
     * 加密：随机 IV + GCM 认证加密 + Base64 编码
     *
     * @param plainText 明文，为 null 时返回 null
     */
    public static String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV 与密文拼接后统一 Base64，解密时再拆分
            byte[] result = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感数据加密失败", e);
        }
    }

    /**
     * 解密。
     *
     * <p>若入参不是本算法产生的密文（例如加密功能上线前遗留的明文），
     * 则原样返回，保证历史数据仍可正常读取，实现平滑迁移。</p>
     *
     * @param cipherText Base64 密文，为 null 时返回 null
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            if (raw.length <= IV_LENGTH) {
                return cipherText;
            }
            byte[] iv = Arrays.copyOfRange(raw, 0, IV_LENGTH);
            byte[] data = Arrays.copyOfRange(raw, IV_LENGTH, raw.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容加密上线前的历史明文数据
            log.debug("字段解密失败，按明文原样返回：{}", e.getMessage());
            return cipherText;
        }
    }
}
