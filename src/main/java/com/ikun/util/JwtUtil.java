package com.ikun.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类（基于 jjwt 0.12.x）
 *
 * <p>Token 分两类，通过 {@code type} 声明区分：</p>
 * <ul>
 *   <li>{@link #TYPE_ADMIN}：后台账号（PC 后台 / 管理端移动版），带 roleId 与 scenicId；</li>
 *   <li>{@link #TYPE_TOURIST}：游客小程序用户，带 touristId 与注册景区。</li>
 * </ul>
 *
 * <p>区分是安全所需而非冗余：两类 Token 用同一个签名密钥，若不带类型标识，
 * 游客 Token 就能通过后台的登录校验，进而在那些「只要求登录、未标注具体权限」
 * 的接口上冒充后台用户。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Component
public class JwtUtil {

    /** 后台账号 Token */
    public static final String TYPE_ADMIN = "ADMIN";
    /** 游客小程序 Token */
    public static final String TYPE_TOURIST = "TOURIST";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire}")
    private Long expire;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成后台账号 Token
     *
     * @param userId   用户ID
     * @param username 登录账号
     * @param roleId   角色ID
     * @param scenicId 所属景区ID（可为空，空表示全部景区）
     */
    public String createToken(Long userId, String username, Long roleId, Long scenicId) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TYPE_ADMIN);
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("roleId", roleId);
        claims.put("scenicId", scenicId);
        return build(claims, String.valueOf(userId));
    }

    /**
     * 生成游客小程序 Token
     *
     * @param touristId 游客ID
     * @param touristNo 游客编号
     * @param scenicId  当前所在景区ID
     */
    public String createTouristToken(Long touristId, String touristNo, Long scenicId) {
        Map<String, Object> claims = new HashMap<>(8);
        claims.put("type", TYPE_TOURIST);
        claims.put("touristId", touristId);
        claims.put("touristNo", touristNo);
        claims.put("scenicId", scenicId);
        // 复用 userId 字段，保证黑名单等通用逻辑无需分支
        claims.put("userId", touristId);
        return build(claims, String.valueOf(touristId));
    }

    private String build(Map<String, Object> claims, String subject) {
        // jti（JWT ID）：Token 唯一标识，退出登录时以此为 key 写入 Redis 黑名单
        claims.put("jti", UUID.randomUUID().toString().replace("-", ""));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expire))
                .signWith(getKey())
                .compact();
    }

    /** 解析 Token，失败返回 null */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("Token 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /** 校验 Token 是否有效（签名正确且未过期） */
    public boolean validate(String token) {
        Claims claims = parseToken(token);
        return claims != null && claims.getExpiration().after(new Date());
    }

    /** 读取 Token 类型（ADMIN / TOURIST），解析失败返回 null */
    public String getType(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.get("type", String.class);
    }

    /** 通用取字段，类型不匹配时 jjwt 会抛异常，这里统一吞掉返回 null */
    public <T> T getClaim(String token, String key, Class<T> type) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        try {
            return claims.get(key, type);
        } catch (Exception e) {
            log.debug("Token 字段 {} 类型转换失败：{}", key, e.getMessage());
            return null;
        }
    }

    /** 从 Token 中获取用户ID（后台为 sys_user.id，游客为 tourist.id） */
    public Long getUserId(String token) {
        return getClaim(token, "userId", Long.class);
    }

    /** 获取 Token 唯一标识（jti），作为黑名单的存储 key */
    public String getJti(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        String jti = claims.get("jti", String.class);
        return jti != null ? jti : claims.getId();
    }

    /** 获取 Token 过期时间，用于设置黑名单条目的存活时间 */
    public Date getExpiration(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.getExpiration();
    }
}
