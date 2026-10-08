package com.ikun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游客列表/详情视图对象
 *
 * <p>手机号与证件号在输出前一律脱敏。列表页用于客服核对身份，
 * 展示完整证件号既不必要，也违反个人信息最小化原则。</p>
 *
 * @author smart-scenic
 */
@Data
@Schema(description = "游客信息")
public class TouristVO implements Serializable {

    @Schema(description = "游客ID")
    private Long id;

    @Schema(description = "游客编号")
    private String touristNo;

    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "手机号（已脱敏）")
    private String phone;

    @Schema(description = "证件号码（已脱敏）")
    private String idCard;

    @Schema(description = "性别：1男 2女 0未知")
    private Integer gender;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "注册来源：MINI_PROGRAM/OTA/WINDOW")
    private String source;

    @Schema(description = "会员等级：NORMAL/SILVER/GOLD/DIAMOND")
    private String memberLevel;

    @Schema(description = "积分")
    private Integer points;

    @Schema(description = "实名状态：1已实名 0未实名")
    private Integer realNameStatus;

    @Schema(description = "是否黑名单：1是 0否")
    private Integer isBlacklist;

    @Schema(description = "黑名单原因")
    private String blacklistReason;

    @Schema(description = "加入黑名单时间")
    private LocalDateTime blacklistTime;

    @Schema(description = "最近入园时间")
    private LocalDateTime lastEnterTime;

    @Schema(description = "注册来源景区ID，NULL 表示全平台注册")
    private Long registerScenicId;

    @Schema(description = "状态：1正常 0禁用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "注册时间")
    private LocalDateTime createTime;

    @Schema(description = "累计订单数，仅详情接口返回")
    private Long orderCount;

    @Schema(description = "累计提交工单数，仅详情接口返回")
    private Long complaintCount;
}
