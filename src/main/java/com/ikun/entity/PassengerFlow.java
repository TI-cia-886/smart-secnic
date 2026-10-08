package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客流统计表
 *
 * @author smart-scenic
 */
@Data
@TableName("passenger_flow")
public class PassengerFlow implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 统计时段（小时 0-23） */
    private Integer statHour;

    /** 入园人数 */
    private Integer enterCount;

    /** 出园人数 */
    private Integer leaveCount;

    /** 在园人数 */
    private Integer currentCount;

    /** 该时段承载量 */
    private Integer capacity;

    /** 预警级别：NORMAL/WARNING/DANGER */
    private String warningLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
