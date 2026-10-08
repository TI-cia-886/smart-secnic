package com.ikun.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP 请求信息工具：客户端 IP、浏览器、操作系统解析
 *
 * <p>登录日志与操作日志都要记录来源信息，统一收敛到这里，
 * 避免两处各写一套解析逻辑导致结果不一致。</p>
 *
 * @author smart-scenic
 */
public final class RequestUtil {

    /** 常见代理头，按优先级从高到低排列 */
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
    };

    private RequestUtil() {
    }

    /** 获取当前请求，非 Web 线程（如定时任务、异步线程）返回 null */
    public static HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP。
     *
     * <p>经过 Nginx 等反向代理后，{@code getRemoteAddr()} 拿到的是代理地址，
     * 必须优先读转发头。{@code X-Forwarded-For} 可能是逗号分隔的链路，
     * 取第一个才是真实客户端。</p>
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0 ? value.substring(0, comma) : value).trim();
            }
        }
        return request.getRemoteAddr();
    }

    /** 解析浏览器名称与主版本号，如「Chrome 120」 */
    public static String getBrowser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return parseBrowser(request.getHeader("User-Agent"));
    }

    /** 解析操作系统，如「Windows 11」「macOS 14」 */
    public static String getOs(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return parseOs(request.getHeader("User-Agent"));
    }

    static String parseBrowser(String ua) {
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        // 顺序敏感：Edge 的 UA 里同时含 Chrome 与 Safari，必须先判断
        if (ua.contains("Edg/")) {
            return "Edge " + major(ua, "Edg/");
        }
        if (ua.contains("MicroMessenger/")) {
            return "WeChat " + major(ua, "MicroMessenger/");
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera " + major(ua, "OPR/");
        }
        if (ua.contains("Firefox/")) {
            return "Firefox " + major(ua, "Firefox/");
        }
        if (ua.contains("Chrome/")) {
            return "Chrome " + major(ua, "Chrome/");
        }
        if (ua.contains("Safari/")) {
            return "Safari " + major(ua, "Version/");
        }
        return ua.length() > 100 ? ua.substring(0, 100) : ua;
    }

    static String parseOs(String ua) {
        if (!StringUtils.hasText(ua)) {
            return null;
        }
        if (ua.contains("Windows NT 10.0")) {
            return "Windows 10/11";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("iPhone") || ua.contains("iPad")) {
            return "iOS " + major(ua, "OS ");
        }
        if (ua.contains("Android")) {
            return "Android " + major(ua, "Android ");
        }
        if (ua.contains("Mac OS X")) {
            return "macOS " + major(ua, "Mac OS X ");
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "未知";
    }

    /** 从 UA 中截取主版本号（取到第一个非数字字符为止） */
    private static String major(String ua, String anchor) {
        int start = ua.indexOf(anchor);
        if (start < 0) {
            return "";
        }
        start += anchor.length();
        int end = start;
        while (end < ua.length() && (Character.isDigit(ua.charAt(end)) || ua.charAt(end) == '.' || ua.charAt(end) == '_')) {
            end++;
        }
        String raw = ua.substring(start, end).replace('_', '.');
        int dot = raw.indexOf('.');
        return dot > 0 ? raw.substring(0, dot) : raw;
    }
}
