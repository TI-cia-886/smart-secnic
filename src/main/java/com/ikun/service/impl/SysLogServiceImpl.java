package com.ikun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikun.common.BusinessException;
import com.ikun.common.PageResult;
import com.ikun.entity.SysLoginLog;
import com.ikun.entity.SysOperLog;
import com.ikun.mapper.SysLoginLogMapper;
import com.ikun.mapper.SysOperLogMapper;
import com.ikun.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 系统日志查询服务实现
 *
 * @author smart-scenic
 */
@Service
@RequiredArgsConstructor
public class SysLogServiceImpl implements SysLogService {

    private final SysLoginLogMapper sysLoginLogMapper;
    private final SysOperLogMapper sysOperLogMapper;

    @Override
    public PageResult<SysLoginLog> pageLoginLog(Integer pageNum, Integer pageSize, String username,
                                                Integer status, String loginType,
                                                LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<SysLoginLog> wrapper = Wrappers.<SysLoginLog>lambdaQuery()
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .eq(status != null, SysLoginLog::getStatus, status)
                .eq(StringUtils.hasText(loginType), SysLoginLog::getLoginType, loginType)
                .ge(startDate != null, SysLoginLog::getLoginTime, startOfDay(startDate))
                .le(endDate != null, SysLoginLog::getLoginTime, endOfDay(endDate))
                .orderByDesc(SysLoginLog::getLoginTime);
        return PageResult.of(sysLoginLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public PageResult<SysOperLog> pageOperLog(Integer pageNum, Integer pageSize, String title,
                                              String businessType, String operatorName, Integer status,
                                              LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<SysOperLog> wrapper = Wrappers.<SysOperLog>lambdaQuery()
                .like(StringUtils.hasText(title), SysOperLog::getTitle, title)
                .eq(StringUtils.hasText(businessType), SysOperLog::getBusinessType, businessType)
                .like(StringUtils.hasText(operatorName), SysOperLog::getOperatorName, operatorName)
                .eq(status != null, SysOperLog::getStatus, status)
                .ge(startDate != null, SysOperLog::getOperTime, startOfDay(startDate))
                .le(endDate != null, SysOperLog::getOperTime, endOfDay(endDate))
                // 列表不返回大字段，避免单页响应体被参数与返回值撑到几 MB
                .select(SysOperLog.class, field -> !"oper_param".equals(field.getColumn())
                        && !"json_result".equals(field.getColumn()))
                .orderByDesc(SysOperLog::getOperTime);
        return PageResult.of(sysOperLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @Override
    public SysOperLog getOperLog(Long id) {
        SysOperLog log = sysOperLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException("操作日志不存在");
        }
        return log;
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    /** 结束日期按当天 23:59:59 处理，否则等于漏掉当天全部数据 */
    private LocalDateTime endOfDay(LocalDate date) {
        return date == null ? null : LocalDateTime.of(date, LocalTime.MAX);
    }
}
