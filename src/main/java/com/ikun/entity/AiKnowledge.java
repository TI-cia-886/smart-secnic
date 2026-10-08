package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 知识库
 *
 * @author smart-scenic
 */
@Data
@TableName("ai_knowledge")
public class AiKnowledge implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属景区ID，NULL 表示通用 */
    private Long scenicId;

    /** 分类：COMMON/TICKET/ROUTE/SERVICE/SAFETY */
    private String category;

    /** 标准问题 */
    private String question;

    /** 标准答案 */
    private String answer;

    /** 关键词，逗号分隔 */
    private String keywords;

    /** 问题向量化结果（JSON 数组），用于语义检索；不使用向量检索时留空 */
    private String embedding;

    /** 生成该向量的模型名，如 nomic-embed-text */
    private String embeddingModel;

    /** 命中次数 */
    private Integer hitCount;

    /** 状态：1启用 0禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
