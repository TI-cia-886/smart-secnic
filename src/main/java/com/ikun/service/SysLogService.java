package com.ikun.service;

import com.ikun.common.PageResult;
import com.ikun.entity.SysLoginLog;
import com.ikun.entity.SysOperLog;

import java.time.LocalDate;

/**
 * 系统日志查询服务
 *
 * @author smart-scenic
 */
public interface SysLogService {

    /**
     * 分页查询登录日志
     *
     * @param username  账号模糊匹配
     * @param status    结果：1成功 0失败
     * @param loginType 来源客户端
     * @param startDate 起始日期（含）
     * @param endDate   结束日期（含）
     */
    PageResult<SysLoginLog> pageLoginLog(Integer pageNum, Integer pageSize, String username,
                                         Integer status, String loginType,
                                         LocalDate startDate, LocalDate endDate);

    /**
     * 分页查询操作日志
     *
     * @param title         模块标题模糊匹配
     * @param businessType  业务类型
     * @param operatorName  操作人模糊匹配
     * @param status        结果：1成功 0失败
     * @param startDate     起始日期（含）
     * @param endDate       结束日期（含）
     */
    PageResult<SysOperLog> pageOperLog(Integer pageNum, Integer pageSize, String title,
                                       String businessType, String operatorName, Integer status,
                                       LocalDate startDate, LocalDate endDate);

    /** 查询操作日志详情 */
    SysOperLog getOperLog(Long id);
}
