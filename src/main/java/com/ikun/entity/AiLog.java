package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 调用日志表
 *
 * @author smart-scenic
 */
@Data
@TableName("ai_log")
public class AiLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 功能模块：KNOWLEDGE/SENTIMENT/ANNOUNCEMENT/SUMMARY/FLOW_PLAN/ROUTE */
    private String module;

    /** 输入摘要 */
    private String inputSummary;

    /** 输出摘要 */
    private String outputSummary;

    /** 耗时（毫秒） */
    private Integer duration;

    /** 状态：SUCCESS/FAIL */
    private String status;

    /** 错误信息 */
    private String errorMsg;

    /** 操作人ID */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
