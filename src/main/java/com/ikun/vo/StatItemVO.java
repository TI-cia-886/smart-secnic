package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用「名称-数值」统计项
 *
 * <p>饼图、条形图的数据结构在游客统计、客流统计、报表里反复出现，
 * 统一成一个类，前端也只需要写一套渲染逻辑。</p>
 *
 * @author smart-scenic
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统计项（名称-数值）")
public class StatItemVO implements Serializable {

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "数值")
    private Long value;
}
