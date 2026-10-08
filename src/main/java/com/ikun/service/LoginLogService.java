package com.ikun.service;

import com.ikun.entity.SysLoginLog;
import com.ikun.mapper.SysLoginLogMapper;
import com.ikun.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录日志服务
 *
 * <p>登录成功与失败都要落库，失败原因写进 {@code msg}。
 * 记录日志本身不允许影响登录流程——所以这里吞掉所有异常只打告警，
 * 否则数据库抖动会导致所有人都登不进来。</p>
 *
 * @author smart-scenic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SysLoginLogMapper sysLoginLogMapper;

    /**
     * 记录一条登录日志
     *
     * @param userId    用户ID，登录失败时传 null
     * @param username  登录账号
     * @param loginType 来源：PC / MANAGER / MINI_PROGRAM
     * @param success   是否成功
     * @param msg       提示信息或失败原因
     */
    public void record(Long userId, String username, String loginType, boolean success, String msg) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setLoginType(loginType == null ? "PC" : loginType);
            entity.setStatus(success ? 1 : 0);
            entity.setMsg(truncate(msg, 255));
            entity.setLoginTime(LocalDateTime.now());

            HttpServletRequest request = RequestUtil.currentRequest();
            if (request != null) {
                entity.setIp(RequestUtil.getClientIp(request));
                entity.setBrowser(truncate(RequestUtil.getBrowser(request), 100));
                entity.setOs(truncate(RequestUtil.getOs(request), 100));
            }
            sysLoginLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("登录日志写入失败（不影响登录）：{}", e.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
