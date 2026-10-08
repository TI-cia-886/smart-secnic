package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人脸识别记录表
 *
 * <p>记录每次人脸注册 / 搜索 / 比对 / 检测的调用结果，
 * 用于审计与运营分析（如黑名单人员识别命中情况）。</p>
 *
 * @author smart-scenic
 */
@Data
@TableName("face_record")
public class FaceRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 记录类型：ENROLL人脸注册 SEARCH人脸搜索 COMPARE人脸比对 DETECT人脸检测 */
    private String recordType;

    /** 命中的游客ID（仅 SEARCH 有值） */
    private Long touristId;

    /** 命中的游客编号（腾讯云 PersonId，即 tourist_no） */
    private String touristNo;

    /** 命中的游客姓名 */
    private String touristName;

    /** 相似度 / 置信度（0~100，COMPARE / SEARCH 有值） */
    private BigDecimal confidence;

    /** 检测到的人脸数（DETECT 有值） */
    private Integer faceCount;

    /** 结果摘要（检测结果属性 / 比对结论等） */
    private String detail;

    /** 操作人ID（sys_user.id） */
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
