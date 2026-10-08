package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 工单详情视图对象
 *
 * <p>继承列表对象，额外挂上会话式回复列表，
 * 详情页就是「工单主体 + 沟通记录」两段结构。</p>
 *
 * @author smart-scenic
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "工单详情")
public class ComplaintDetailVO extends ComplaintVO {

    @Schema(description = "回复记录，按时间正序")
    private List<ComplaintReplyVO> replies = new ArrayList<>();
}
