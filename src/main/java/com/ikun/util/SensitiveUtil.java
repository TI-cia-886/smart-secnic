package com.ikun.util;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 敏感信息处理工具（手机号校验、脱敏、默认密码、不可逆摘要）
 *
 * <p>手机号在数据库中以 AES 密文存储，密文无法用于条件查询，
 * 因此额外保存一列 {@code phone_hash}（SHA-256 加盐摘要）用于精确检索与唯一性校验。
 * SHA-256 为单向哈希，无法由摘要还原出手机号。</p>
 *
 * @author smart-scenic
 */
@Component
public class SensitiveUtil {

    /** 中国大陆手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 无手机号时使用的兜底初始密码 */
    private static final String FALLBACK_PASSWORD = "123456";

    /** SHA-256 摘要盐值，由 Spring 启动时注入（加盐可抵御彩虹表攻击） */
    private static String salt = "smart-scenic";

    public SensitiveUtil(@Value("${security.phone-hash-salt:}") String phoneHashSalt) {
        if (StringUtils.hasText(phoneHashSalt)) {
            SensitiveUtil.salt = phoneHashSalt;
        }
    }

    /** 是否为中国大陆手机号格式（加密后的密文会返回 false） */
    public static boolean isValidPhone(String phone) {
        return StringUtils.hasText(phone) && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /** 取手机号后六位，作为新账号的默认密码 */
    public static String lastSixDigits(String phone) {
        if (!isValidPhone(phone)) {
            return null;
        }
        String trim = phone.trim();
        return trim.substring(trim.length() - 6);
    }

    /** 默认密码：手机号后六位；手机号缺失或格式非法时退回内置初始密码 */
    public static String defaultPassword(String phone) {
        String lastSix = lastSixDigits(phone);
        return StringUtils.hasText(lastSix) ? lastSix : FALLBACK_PASSWORD;
    }

    /**
     * 手机号不可逆摘要（SHA-256 + 盐），用于精确查询与唯一性约束。
     *
     * @return 64 位十六进制字符串；入参为空时返回 null
     */
    public static String phoneHash(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return DigestUtil.sha256Hex(salt + phone.trim());
    }

    /** 通用 SHA-256 加盐摘要 */
    public static String sha256(String raw) {
        return raw == null ? null : DigestUtil.sha256Hex(salt + raw);
    }

    /** 手机号脱敏，用于日志输出：13800138000 → 138****8000 */
    public static String maskPhone(String phone) {
        if (!isValidPhone(phone)) {
            return "***";
        }
        String trim = phone.trim();
        return trim.substring(0, 3) + "****" + trim.substring(7);
    }
}
