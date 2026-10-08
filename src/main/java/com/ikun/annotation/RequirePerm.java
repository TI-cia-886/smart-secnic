package com.ikun.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限校验注解
 *
 * <p>标注在 Controller 方法上，由 {@code JwtInterceptor} 在登录校验之后比对当前
 * 角色是否持有该权限标识。未标注的接口只要求登录、不要求具体权限。</p>
 *
 * <p>这样做的意义：仅靠前端隐藏菜单不是权限控制——菜单藏起来，
 * 请求仍可直接发到后端。真正的边界必须在服务端。</p>
 *
 * <pre>{@code
 * @RequirePerm("system:user:delete")
 * @DeleteMapping("/{id}")
 * public Result<Void> delete(@PathVariable Long id) { ... }
 * }</pre>
 *
 * @author smart-scenic
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /** 权限标识，与 sys_permission.perm_code 一致，如 system:user:delete */
    String value();

    /**
     * 多个权限标识之间的逻辑关系。
     *
     * @return true 表示需要同时具备全部权限；false 表示具备任一即可
     */
    boolean requireAll() default false;
}
