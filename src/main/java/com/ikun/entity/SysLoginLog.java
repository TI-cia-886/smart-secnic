package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志表
 *
 * <p>登录成功与失败都会写入，失败时 {@code userId} 为空、{@code msg} 记录失败原因。</p>
 *
 * @author smart-scenic
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，登录失败时可能为空 */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 来源：PC后台 MANAGER管理端移动版 MINI_PROGRAM游客小程序 */
    private String loginType;

    /** 登录IP */
    private String ip;

    /** 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 结果：1成功 0失败 */
    private Integer status;

    /** 提示信息 / 失败原因 */
    private String msg;

    /** 登录时间 */
    private LocalDateTime loginTime;
}
