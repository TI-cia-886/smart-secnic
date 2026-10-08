package com.ikun.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikun.common.AppUserContext;
import com.ikun.common.Result;
import com.ikun.common.ResultCode;
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

import java.nio.charset.StandardCharsets;

/**
 * 游客小程序鉴权拦截器
 *
 * <p>只注册在确实需要游客身份的小程序接口上（下单、我的订单、我的工单等），
 * 景区浏览、票种查询这类公开数据不经过本拦截器。</p>
 *
 * <p>与后台拦截器共用签名密钥，因此必须校验 {@code type == TOURIST}，
 * 否则后台 Token 可以当作游客 Token 使用。</p>
 *
 * @author smart-scenic
 */
@Component
@RequiredArgsConstructor
public class AppAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            writeError(response, ResultCode.UNAUTHORIZED, "请先登录后再操作");
            return false;
        }
        if (tokenBlacklistService.isBlacklisted(jwtUtil.getJti(token))) {
            writeError(response, ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
            return false;
        }

        Claims claims = jwtUtil.parseToken(token);
        if (claims == null || !JwtUtil.TYPE_TOURIST.equals(claims.get("type", String.class))) {
            writeError(response, ResultCode.UNAUTHORIZED, "请使用游客身份登录");
            return false;
        }

        AppUserContext.setTouristId(claims.get("touristId", Long.class));
        AppUserContext.setTouristNo(claims.get("touristNo", String.class));
        AppUserContext.setScenicId(claims.get("scenicId", Long.class));
        // 暂存原始 Token，退出登录时据此写入黑名单
        AppUserContext.setToken(token);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
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

    private void writeError(HttpServletResponse response, ResultCode code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code.getCode(), message)));
    }
}
