package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单回复视图对象
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "工单回复")
public class ComplaintReplyVO implements Serializable {

    @Schema(description = "回复ID")
    private Long id;

    @Schema(description = "工单ID")
    private Long complaintId;

    @Schema(description = "回复方码：TOURIST/STAFF/AI")
    private String replyType;

    @Schema(description = "回复方中文")
    private String replyTypeText;

    @Schema(description = "回复内容")
    private String content;

    @Schema(description = "回复人名称")
    private String replyBy;

    @Schema(description = "该条回复的情感倾向")
    private String sentiment;

    @Schema(description = "回复时间")
    private LocalDateTime createTime;
}
