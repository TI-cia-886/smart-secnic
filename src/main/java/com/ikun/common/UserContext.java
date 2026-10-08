package com.ikun.common;

/**
 * 后台登录用户上下文（基于 ThreadLocal），供 Service 层获取登录信息
 *
 * <p>拦截器 {@code preHandle} 写入、{@code afterCompletion} 清理。
 * 清理是必需的：Tomcat 复用线程，一旦漏清，下一个请求会读到上一个用户的身份。</p>
 *
 * <p><b>注意</b>：ThreadLocal 不跨线程。后续若引入线程池或定时任务，
 * 异步分支里这些方法会返回 null，需要显式把身份参数传进去，
 * 或改用可透传的 ThreadLocal 实现。</p>
 *
 * @author smart-scenic
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> ROLE_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> SCENIC_ID = new ThreadLocal<>();
    /** 当前请求携带的原始 Token，用于退出登录时写入黑名单 */
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void setRoleId(Long roleId) {
        ROLE_ID.set(roleId);
    }

    public static Long getRoleId() {
        return ROLE_ID.get();
    }

    public static void setScenicId(Long scenicId) {
        SCENIC_ID.set(scenicId);
    }

    public static Long getScenicId() {
        return SCENIC_ID.get();
    }

    public static void setToken(String token) {
        TOKEN.set(token);
    }

    public static String getToken() {
        return TOKEN.get();
    }

    /** 请求结束后务必清理，防止线程复用造成数据串号 */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        ROLE_ID.remove();
        SCENIC_ID.remove();
        TOKEN.remove();
    }
}
