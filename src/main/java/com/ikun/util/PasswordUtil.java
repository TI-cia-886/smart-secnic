package com.ikun.util;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;

import java.util.regex.Pattern;

/**
 * 密码加密与校验工具（BCrypt，单向不可逆）
 *
 * <p>BCrypt 特点：</p>
 * <ul>
 *   <li>单向哈希，无法由密文反推明文；</li>
 *   <li>自带随机盐（每次加密结果都不同），可抵御彩虹表与批量撞库；</li>
 *   <li>计算成本可调（cost 因子），能有效抵御暴力破解。</li>
 * </ul>
 *
 * <p>同时兼容历史 MD5 密码：老账号仍可用原密码登录，
 * 登录成功后由业务层自动把存储值升级为 BCrypt。</p>
 *
 * @author smart-scenic
 */
public class PasswordUtil {

    /** 32 位十六进制 = 历史 MD5 存储格式 */
    private static final Pattern LEGACY_MD5_PATTERN = Pattern.compile("^[a-fA-F0-9]{32}$");

    private PasswordUtil() {
    }

    /** 加密明文密码，返回 60 位 BCrypt 哈希值 */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 校验明文密码与数据库中的哈希值是否匹配。
     *
     * @param rawPassword     用户输入的明文密码
     * @param storedPassword  数据库中的哈希值（BCrypt 或历史 MD5）
     */
    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        // 历史 MD5 数据：先按 MD5 校验，登录成功后会自动升级为 BCrypt
        if (isLegacyMd5(storedPassword)) {
            return storedPassword.equalsIgnoreCase(DigestUtil.md5Hex(rawPassword));
        }
        try {
            return BCrypt.checkpw(rawPassword, storedPassword);
        } catch (Exception e) {
            // 哈希格式非法时视为校验失败，不向外抛出
            return false;
        }
    }

    /** 是否为历史 MD5 格式的密码 */
    public static boolean isLegacyMd5(String storedPassword) {
        return storedPassword != null && LEGACY_MD5_PATTERN.matcher(storedPassword).matches();
    }
}
