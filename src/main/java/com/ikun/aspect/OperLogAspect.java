package com.ikun.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikun.annotation.OperLog;
import com.ikun.common.UserContext;
import com.ikun.entity.SysOperLog;
import com.ikun.mapper.SysOperLogMapper;
import com.ikun.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 操作日志切面
 *
 * <p>围绕 {@link OperLog} 注解的方法记录操作明细，业务代码零侵入。</p>
 *
 * <p>三点设计取舍：</p>
 * <ul>
 *   <li><b>记日志失败绝不影响业务</b>：整个落库过程包在 try/catch 里，
 *       日志表写不进去只打告警，接口该成功还是成功。</li>
 *   <li><b>同步写入而非异步</b>：异步线程拿不到 {@code UserContext} 的 ThreadLocal
 *       （操作人信息会丢），且本项目的日志量不值得为它引入线程池与上下文透传的复杂度。</li>
 *   <li><b>参数脱敏</b>：password 一类的字段在落库前替换为 ***，
 *       避免把明文密码永久沉淀到日志表里。</li>
 * </ul>
 *
 * @author smart-scenic
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    /** 需要脱敏的参数名（小写包含匹配） */
    private static final List<String> SENSITIVE_KEYS = List.of("password", "oldpassword", "newpassword", "idcard");

    /** 单字段最大长度，超出截断，防止日志表被超大报文撑爆 */
    private static final int MAX_TEXT = 60000;

    private final SysOperLogMapper sysOperLogMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            saveLog(joinPoint, operLog, result, error, System.currentTimeMillis() - start);
        }
    }

    /** 组装并落库操作日志，任何异常都不得向外抛出 */
    private void saveLog(ProceedingJoinPoint joinPoint, OperLog operLog,
                         Object result, Throwable error, long cost) {
        try {
            SysOperLog entity = new SysOperLog();
            entity.setTitle(operLog.title());
            entity.setBusinessType(operLog.businessType());
            entity.setStatus(error == null ? 1 : 0);
            entity.setCostTime(cost);
            entity.setOperTime(LocalDateTime.now());
            entity.setOperatorId(UserContext.getUserId());
            entity.setOperatorName(UserContext.getUsername());
            entity.setScenicId(UserContext.getScenicId());

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            entity.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());

            HttpServletRequest request = RequestUtil.currentRequest();
            if (request != null) {
                entity.setRequestMethod(request.getMethod());
                entity.setOperUrl(truncate(request.getRequestURI(), 255));
                entity.setOperIp(truncate(RequestUtil.getClientIp(request), 64));
            }

            if (operLog.saveParam()) {
                entity.setOperParam(truncate(toJson(desensitize(joinPoint.getArgs())), MAX_TEXT));
            }
            if (operLog.saveResult() && error == null) {
                entity.setJsonResult(truncate(toJson(result), MAX_TEXT));
            }
            if (error != null) {
                entity.setErrorMsg(truncate(error.getMessage(), 2000));
            }

            sysOperLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("操作日志写入失败（不影响业务）：{}", e.getMessage());
        }
    }

    /** 把可能触发 Jackson 循环/超大对象转换的参数替换成占位符 */
    private Object[] desensitize(Object[] args) {
        if (args == null) {
            return new Object[0];
        }
        Object[] copy = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            copy[i] = isSerializable(args[i]) ? mask(args[i]) : "[不可序列化参数]";
        }
        return copy;
    }

    /** 过滤 Servlet 对象等无法/不应序列化的参数 */
    private boolean isSerializable(Object arg) {
        if (arg == null) {
            return false;
        }
        return !(arg instanceof HttpServletRequest
                || arg instanceof jakarta.servlet.http.HttpServletResponse
                || arg instanceof java.io.InputStream
                || arg instanceof java.io.OutputStream);
    }

    /** 基于 Map 做字段级脱敏（DTO 经 Jackson 转换后即为 Map） */
    private Object mask(Object arg) {
        try {
            Object node = objectMapper.convertValue(arg, Object.class);
            maskNode(node);
            return node;
        } catch (Exception e) {
            // 某些类型 Jackson 转不动（如无 getter 的匿名对象），退化为 toString
            return String.valueOf(arg);
        }
    }

    @SuppressWarnings("unchecked")
    private void maskNode(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> mutable = (Map<Object, Object>) map;
            for (Map.Entry<Object, Object> entry : mutable.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase();
                if (SENSITIVE_KEYS.stream().anyMatch(key::contains)) {
                    entry.setValue("***");
                } else {
                    maskNode(entry.getValue());
                }
            }
        } else if (node instanceof List<?> list) {
            list.forEach(this::maskNode);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[JSON 序列化失败]";
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
