package com.ikun.interceptor;

import com.ikun.common.AppUserContext;
import com.ikun.service.TokenBlacklistService;
import com.ikun.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 游客小程序「可选登录」拦截器
 *
 * <p>首页、景区浏览这类接口既要允许未登录游客访问，又希望已登录用户能看到
 * 「我的待支付订单数」这类个性化内容。要求登录会把游客挡在门外，完全不解析
 * Token 又拿不到身份，于是单独提供一个「有 Token 就识别、没有也放行」的拦截器。</p>
 *
 * <p>它只负责补齐身份，不做任何拒绝；真正的访问控制仍由
 * {@link AppAuthInterceptor} 在需要登录的接口上强制完成。</p>
 *
 * @author smart-scenic
 */
@Component
@RequiredArgsConstructor
public class AppOptionalAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        // 任何一项不满足都按匿名处理：签名非法、已过期、已登出或不是游客 Token
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            return true;
        }
        if (tokenBlacklistService.isBlacklisted(jwtUtil.getJti(token))) {
            return true;
        }
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null || !JwtUtil.TYPE_TOURIST.equals(claims.get("type", String.class))) {
            return true;
        }
        AppUserContext.setTouristId(claims.get("touristId", Long.class));
        AppUserContext.setTouristNo(claims.get("touristNo", String.class));
        AppUserContext.setScenicId(claims.get("scenicId", Long.class));
        AppUserContext.setToken(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 未登录时 ThreadLocal 本就是空的，统一清理不会产生副作用
        AppUserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(header);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (StringUtils.hasText(tokenPrefix) && token.startsWith(tokenPrefix)) {
            return token.substring(tokenPrefix.length()).trim();
        }
        return token.trim();
    }
}
