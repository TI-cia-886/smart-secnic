package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话记录表
 *
 * @author smart-scenic
 */
@Data
@TableName("ai_chat")
public class AiChat implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 游客ID */
    private Long touristId;

    /** 景区ID */
    private Long scenicId;

    /** 角色：user游客 assistant智能助手 */
    private String role;

    /** 消息内容 */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
