package com.ikun.service;

import com.ikun.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;

/**
 * JWT Token 黑名单服务（基于 Redis 实现登出即失效）
 *
 * <p>JWT 是无状态的，签发后在有效期内始终有效，服务端无法主动作废。
 * 为了支持"退出登录后 Token 立即失效"，这里把已登出的 Token 标识（jti）
 * 写入 Redis 黑名单，鉴权拦截器每次校验时先查黑名单。</p>
 *
 * <p>过期策略：黑名单条目的 TTL 与 Token 剩余有效期一致，
 * Token 自然过期后黑名单自动清除，不会造成 Redis 无限膨胀。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    /** 黑名单 key 前缀 */
    private static final String KEY_PREFIX = "jwt:blacklist:";

    /** Token 无过期时间时的兜底 TTL */
    private static final Duration FALLBACK_TTL = Duration.ofDays(1);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将 Token 加入黑名单（登出）
     *
     * @param jti        Token 唯一标识
     * @param expiration Token 过期时间
     */
    public void blacklist(String jti, Date expiration) {
        if (!StringUtils.hasText(jti)) {
            return;
        }
        long ttl = expiration == null
                ? FALLBACK_TTL.toMillis()
                : Math.max(expiration.getTime() - System.currentTimeMillis(), 1000L);
        try {
            stringRedisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofMillis(ttl));
        } catch (Exception e) {
            // 这里不能静默失败：写不进黑名单意味着 Token 仍然有效，
            // 必须让调用方感知，否则用户以为已登出、实际登录态还在
            log.error("Token 黑名单写入失败，Redis 不可用", e);
            throw new BusinessException("退出登录失败：缓存服务不可用，请稍后重试");
        }
        log.info("Token 已加入黑名单：jti={}，剩余有效期 {} 秒", jti, ttl / 1000);
    }

    /**
     * 判断 Token 是否已进入黑名单
     *
     * <p>Redis 不可用时不阻断业务（返回 false 并记录告警），
     * 避免缓存故障导致整个系统无法登录。</p>
     */
    public boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Token 黑名单校验失败（Redis 异常），本次放行：{}", e.getMessage());
            return false;
        }
    }
}
