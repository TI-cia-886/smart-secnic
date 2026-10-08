package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检票核验记录表
 *
 * @author smart-scenic
 */
@Data
@TableName("checkin_record")
public class CheckinRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 景区ID */
    private Long scenicId;

    /** 检票口 */
    private String gate;

    /** 票种名称 */
    private String ticketName;

    /** 核验数量 */
    private Integer quantity;

    /** 核验方式：QRCODE/IDCARD/FACE/MANUAL */
    private String verifyType;

    /** 结果：SUCCESS成功 FAIL失败 */
    private String status;

    /** 失败原因 */
    private String failReason;

    /** 核验时间 */
    private LocalDateTime verifyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
