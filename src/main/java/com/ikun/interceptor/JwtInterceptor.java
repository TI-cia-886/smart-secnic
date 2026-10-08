package com.ikun.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikun.annotation.RequirePerm;
import com.ikun.common.Result;
import com.ikun.common.ResultCode;
import com.ikun.common.UserContext;
import com.ikun.service.PermissionService;
import com.ikun.service.TokenBlacklistService;
import com.ikun.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 后台鉴权拦截器
 *
 * <p>职责分两层，缺一不可：</p>
 * <ol>
 *   <li><b>认证</b>：Token 签名有效、未过期、未被登出（Redis 黑名单）、且类型为后台账号；</li>
 *   <li><b>授权</b>：方法上若标注 {@link RequirePerm}，比对当前角色是否持有该权限。</li>
 * </ol>
 *
 * <p>只有第一层就是「登录后能调所有接口」——这正是本项目改造前的问题。
 * 真正的权限边界必须落在服务端，前端隐藏菜单不是权限控制。</p>
 *
 * @author smart-scenic
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final TokenBlacklistService tokenBlacklistService;
    private final PermissionService permissionService;

    @Value("${jwt.header}")
    private String header;

    @Value("${jwt.token-prefix}")
    private String tokenPrefix;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validate(token)) {
            writeError(response, ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getMessage());
            return false;
        }

        // 黑名单校验：已退出登录的 Token 立即失效，无需等待自然过期
        if (tokenBlacklistService.isBlacklisted(jwtUtil.getJti(token))) {
            writeError(response, ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
            return false;
        }

        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            writeError(response, ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getMessage());
            return false;
        }

        // Token 类型校验：游客小程序的 Token 不能用来访问后台接口
        String type = claims.get("type", String.class);
        if (!JwtUtil.TYPE_ADMIN.equals(type)) {
            writeError(response, ResultCode.UNAUTHORIZED, "请使用后台账号登录");
            return false;
        }

        Long userId = claims.get("userId", Long.class);
        Long roleId = claims.get("roleId", Long.class);

        UserContext.setUserId(userId);
        UserContext.setUsername(claims.get("username", String.class));
        UserContext.setRoleId(roleId);
        UserContext.setScenicId(claims.get("scenicId", Long.class));
        // 暂存原始 Token，退出登录时据此写入黑名单
        UserContext.setToken(token);

        // 授权校验：方法级权限注解
        if (!checkPermission(handler, roleId)) {
            writeError(response, ResultCode.FORBIDDEN, "无权访问该功能，请联系管理员分配权限");
            return false;
        }
        return true;
    }

    /**
     * 校验方法上声明的权限
     *
     * <p>非 Controller 方法（静态资源、错误页等）没有注解，直接放行。</p>
     */
    private boolean checkPermission(Object handler, Long roleId) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePerm requirePerm = handlerMethod.getMethodAnnotation(RequirePerm.class);
        if (requirePerm == null) {
            return true;
        }
        String[] codes = Arrays.stream(requirePerm.value().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (codes.length == 0) {
            return true;
        }
        if (requirePerm.requireAll()) {
            for (String code : codes) {
                if (!permissionService.hasPermission(roleId, code)) {
                    return false;
                }
            }
            return true;
        }
        return permissionService.hasAnyPermission(roleId, codes);
    }

    /** 从请求头解析 Token，去掉 "Bearer " 前缀 */
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 写出错误响应。
     *
     * <p>HTTP 状态码统一保持 200，业务结果由响应体的 {@code code} 表达——
     * 这是本项目的既定约定，前端必须按 body.code 判断成败，不能看 HTTP 状态。</p>
     */
    private void writeError(HttpServletResponse response, ResultCode resultCode, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(resultCode.getCode(), message)));
    }
}
