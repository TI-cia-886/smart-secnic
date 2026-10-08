package com.ikun.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * <p>标注在 Controller 方法上，由 {@code OperLogAspect} 切面在方法执行后
 * 自动写入 {@code sys_oper_log} 表，业务代码无需关心日志落库。</p>
 *
 * <pre>{@code
 * @OperLog(title = "账号管理", businessType = "DELETE")
 * @DeleteMapping("/{id}")
 * public Result<Void> delete(@PathVariable Long id) { ... }
 * }</pre>
 *
 * <p>记日志失败不应影响业务：切面内部已捕获全部异常，只打印告警。</p>
 *
 * @author smart-scenic
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 模块标题，如「账号管理」 */
    String title();

    /** 业务类型：INSERT新增 UPDATE修改 DELETE删除 EXPORT导出 IMPORT导入 GRANT授权 OTHER其它 */
    String businessType() default "OTHER";

    /** 是否记录请求参数 */
    boolean saveParam() default true;

    /**
     * 是否记录返回结果。
     *
     * <p>列表查询、导出等接口返回体很大，建议置为 false，避免日志表膨胀。</p>
     */
    boolean saveResult() default true;
}
