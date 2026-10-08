package com.ikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikun.entity.TicketStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 门票日期库存 Mapper
 *
 * @author smart-scenic
 */
@Mapper
public interface TicketStockMapper extends BaseMapper<TicketStock> {

    /**
     * 锁定库存（下单）。
     *
     * <p>把「判断余量」和「扣减」合并到一条 SQL 的 WHERE 里，
     * 依赖 InnoDB 行锁保证并发下不会超卖——这是防超卖的关键，
     * 绝不能先 SELECT 再 UPDATE。</p>
     *
     * @return 影响行数，0 表示库存不足
     */
    @Update("""
            UPDATE ticket_stock
               SET locked_count = locked_count + #{quantity},
                   update_time  = NOW()
             WHERE ticket_type_id = #{ticketTypeId}
               AND stock_date     = #{stockDate}
               AND status         = 1
               AND total_stock - sold_count - locked_count >= #{quantity}
            """)
    int lockStock(@Param("ticketTypeId") Long ticketTypeId,
                  @Param("stockDate") java.time.LocalDate stockDate,
                  @Param("quantity") Integer quantity);

    /** 释放锁定库存（取消订单 / 超时未支付） */
    @Update("""
            UPDATE ticket_stock
               SET locked_count = GREATEST(locked_count - #{quantity}, 0),
                   update_time  = NOW()
             WHERE ticket_type_id = #{ticketTypeId}
               AND stock_date     = #{stockDate}
            """)
    int releaseStock(@Param("ticketTypeId") Long ticketTypeId,
                     @Param("stockDate") java.time.LocalDate stockDate,
                     @Param("quantity") Integer quantity);

    /** 锁定转已售（支付成功） */
    @Update("""
            UPDATE ticket_stock
               SET locked_count = GREATEST(locked_count - #{quantity}, 0),
                   sold_count   = sold_count + #{quantity},
                   update_time  = NOW()
             WHERE ticket_type_id = #{ticketTypeId}
               AND stock_date     = #{stockDate}
            """)
    int confirmStock(@Param("ticketTypeId") Long ticketTypeId,
                     @Param("stockDate") java.time.LocalDate stockDate,
                     @Param("quantity") Integer quantity);

    /** 已售回滚（退款成功） */
    @Update("""
            UPDATE ticket_stock
               SET sold_count  = GREATEST(sold_count - #{quantity}, 0),
                   update_time = NOW()
             WHERE ticket_type_id = #{ticketTypeId}
               AND stock_date     = #{stockDate}
            """)
    int rollbackSold(@Param("ticketTypeId") Long ticketTypeId,
                     @Param("stockDate") java.time.LocalDate stockDate,
                     @Param("quantity") Integer quantity);
}
