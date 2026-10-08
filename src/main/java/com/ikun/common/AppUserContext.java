package com.ikun.common;

/**
 * 游客小程序登录用户上下文
 *
 * <p>与后台的 {@link UserContext} 刻意分开存放：游客身份和后台账号是两套完全
 * 不同的主体，混在一个 ThreadLocal 里迟早会写出「游客 token 拿到了后台身份」
 * 这类越权漏洞。Token 层面也通过 {@code type} 声明区分，见 {@code JwtUtil}。</p>
 *
 * @author smart-scenic
 */
public class AppUserContext {

    private static final ThreadLocal<Long> TOURIST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TOURIST_NO = new ThreadLocal<>();
    private static final ThreadLocal<Long> SCENIC_ID = new ThreadLocal<>();
    /** 当前请求携带的原始 Token，用于退出登录时写入黑名单 */
    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private AppUserContext() {
    }

    public static void setTouristId(Long touristId) {
        TOURIST_ID.set(touristId);
    }

    /** 返回 null 表示当前是未登录的匿名游客 */
    public static Long getTouristId() {
        return TOURIST_ID.get();
    }

    public static void setTouristNo(String touristNo) {
        TOURIST_NO.set(touristNo);
    }

    public static String getTouristNo() {
        return TOURIST_NO.get();
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

    /** 判断当前是否为已登录游客 */
    public static boolean isLoggedIn() {
        return TOURIST_ID.get() != null;
    }

    /** 请求结束后务必清理 */
    public static void clear() {
        TOURIST_ID.remove();
        TOURIST_NO.remove();
        SCENIC_ID.remove();
        TOKEN.remove();
    }
}
