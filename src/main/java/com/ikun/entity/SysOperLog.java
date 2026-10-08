package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志表（由 AOP 切面自动写入）
 *
 * @author smart-scenic
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块标题，如「账号管理」 */
    private String title;

    /** 业务类型：INSERT/UPDATE/DELETE/EXPORT/IMPORT/GRANT/OTHER */
    private String businessType;

    /** 方法全路径 */
    private String method;

    /** HTTP 方法 */
    private String requestMethod;

    /** 请求地址 */
    private String operUrl;

    /** 操作IP */
    private String operIp;

    /** 请求参数（JSON，敏感字段已脱敏） */
    private String operParam;

    /** 返回结果（JSON） */
    private String jsonResult;

    /** 结果：1成功 0失败 */
    private Integer status;

    /** 异常信息 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costTime;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人账号 */
    private String operatorName;

    /** 操作时所在景区 */
    private Long scenicId;

    /** 操作时间 */
    private LocalDateTime operTime;
}
