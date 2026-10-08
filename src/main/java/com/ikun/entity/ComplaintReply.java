package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单回复表
 *
 * @author smart-scenic
 */
@Data
@TableName("complaint_reply")
public class ComplaintReply implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单ID */
    private Long complaintId;

    /** 回复方：TOURIST游客 STAFF客服 AI智能助手 */
    private String replyType;

    /** 回复内容 */
    private String content;

    /** 回复人名称 */
    private String replyBy;

    /** AI情感倾向：POSITIVE/NEUTRAL/NEGATIVE */
    private String sentiment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
