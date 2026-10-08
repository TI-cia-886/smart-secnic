package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门票日期库存表
 *
 * <p>{@code ticket_type.stock} 只是「每日库存总量」的默认值，
 * 真正按游玩日期扣减库存依赖本表。下单锁 {@code lockedCount}，
 * 支付转 {@code soldCount}，取消回滚。</p>
 *
 * @author smart-scenic
 */
@Data
@TableName("ticket_stock")
public class TicketStock implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 景区ID */
    private Long scenicId;

    /** 票种ID */
    private Long ticketTypeId;

    /** 库存日期（游玩日期） */
    private LocalDate stockDate;

    /** 当日总库存 */
    private Integer totalStock;

    /** 已售数量（已支付） */
    private Integer soldCount;

    /** 锁定数量（已下单未支付） */
    private Integer lockedCount;

    /** 状态：1开放 0停售 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 剩余可售数量 = 总库存 - 已售 - 锁定 */
    public int remain() {
        int total = totalStock == null ? 0 : totalStock;
        int sold = soldCount == null ? 0 : soldCount;
        int locked = lockedCount == null ? 0 : lockedCount;
        return Math.max(total - sold - locked, 0);
    }
}
