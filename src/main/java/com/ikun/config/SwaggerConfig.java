package com.ikun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（SpringDoc OpenAPI 3 / Swagger UI）
 *
 * <p>UI 地址：http://localhost:8080/api/swagger-ui/index.html</p>
 * <p>JSON 描述：http://localhost:8080/api/v3/api-docs</p>
 *
 * @author smart-scenic
 */
@Configuration
public class SwaggerConfig {

    /**
     * 安全方案名称，同时作为请求头名称使用。
     * 与 application-dev.yml 中的 jwt.header 保持一致（Authorization）。
     */
    private static final String SECURITY_SCHEME_NAME = "Authorization";

    @Bean
    public OpenAPI smartScenicOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能AI景区管理系统 API")
                        .description("""
                                后台管理端、管理端移动版、游客小程序 服务端接口文档

                                【鉴权说明】
                                1. 先调用 POST /auth/login 获取 token；
                                2. 点击页面右上角 Authorize 按钮，粘贴 token 本身
                                   （不要自己加 "Bearer " 前缀，Swagger UI 会自动补齐）；
                                3. 之后所有请求都会自动携带请求头 Authorization: Bearer <token>。
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("smart-scenic").email("smart-scenic@example.com")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT 令牌，实际请求头形如：Bearer eyJhbGciOi...")))
                // 全局生效：所有接口默认都需要该请求头
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
