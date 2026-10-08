package com.ikun.config;

import com.ikun.interceptor.AppAuthInterceptor;
import com.ikun.interceptor.AppOptionalAuthInterceptor;
import com.ikun.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：跨域、静态资源映射、拦截器注册
 *
 * @author smart-scenic
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AppAuthInterceptor appAuthInterceptor;
    private final AppOptionalAuthInterceptor appOptionalAuthInterceptor;

    @Value("${file.upload-path}")
    private String uploadPath;

    @Value("${file.access-prefix}")
    private String accessPrefix;

    /** 跨域配置：前后端分离部署时使用 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** 静态资源映射：上传文件可通过 /upload/xxx 访问 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(accessPrefix + "**")
                .addResourceLocations("file:" + uploadPath);
        // 接口文档静态资源：Swagger UI 由 springdoc 自动注册，这里保留 webjars 兜底
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /** 注册登录鉴权拦截器 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 登录 / 注册 / 验证码
                        // 注意：/auth/logout 不在此列 —— 退出登录必须携带有效 Token，
                        // 拦截器才能解析出 jti 并写入 Redis 黑名单
                        "/auth/login",
                        "/auth/register",
                        "/auth/captcha",
                        // 游客小程序（免登录）接口
                        "/app/**",
                        // 接口文档（Swagger UI）
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        // Druid 监控
                        "/druid/**",
                        // 第三方服务配置：前端启动时拉取 AK，必须免登录
                        "/config/**",
                        // 静态资源
                        "/upload/**",
                        "/favicon.ico",
                        "/error"
                );

        // 游客小程序中需要身份的接口，单独用游客 Token 校验。
        // 不能把整个 /app/** 都要求登录：景区浏览、票种查询这类数据游客未登录就该能看。
        registry.addInterceptor(appAuthInterceptor)
                .addPathPatterns(
                        "/app/auth/logout",
                        "/app/order/**",
                        "/app/tourist/**",
                        "/app/complaint/**"
                );

        // 可选登录：首页、景区浏览等公开接口，带 Token 时识别身份（如待支付订单数），
        // 不带 Token 也放行，不会影响游客正常浏览。
        registry.addInterceptor(appOptionalAuthInterceptor)
                .addPathPatterns(
                        "/app/home",
                        "/app/scenic/**",
                        "/app/spot/**",
                        "/app/ticket/**",
                        "/app/ai/**"
                );
    }
}
