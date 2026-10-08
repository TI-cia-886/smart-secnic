package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告资讯表
 *
 * @author smart-scenic
 */
@Data
@TableName("announcement")
public class Announcement implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属景区ID，NULL 表示全平台 */
    private Long scenicId;

    /** 标题 */
    private String title;

    /** 正文内容 */
    private String content;

    /** 类型：NOTICE公告 WARNING预警 ACTIVITY活动 */
    private String type;

    /** 状态：DRAFT草稿 PUBLISHED已发布 OFFLINE已下线 */
    private String status;

    /** 是否置顶：1是 0否 */
    private Integer isTop;

    /** 是否由AI生成草稿：1是 0否 */
    private Integer aiGenerated;

    /** AI生成时使用的提示词，便于复现 */
    private String aiPrompt;

    /** 发布人ID */
    private Long publisherId;

    /** 发布时间 */
    private LocalDateTime publishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
