package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 游客信息表
 *
 * @author smart-scenic
 */
@Data
@TableName("tourist")
public class Tourist implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游客编号 */
    private String touristNo;

    /** 微信 openid */
    private String openid;

    /** 注册来源景区ID（游客从哪个景区入口注册），用于后台按景区过滤 */
    private Long registerScenicId;

    /** 姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 手机号 SHA-256 加盐摘要（不可逆），用于精确检索与唯一性校验 */
    private String phoneHash;

    /** 登录密码（BCrypt 单向哈希，不可逆） */
    private String password;

    /** 证件号码 */
    private String idCard;

    /** 性别：1男 2女 0未知 */
    private Integer gender;

    /** 头像 */
    private String avatar;

    /** 注册来源：MINI_PROGRAM/OTA/WINDOW */
    private String source;

    /** 会员等级：NORMAL/SILVER/GOLD/DIAMOND */
    private String memberLevel;

    /** 积分 */
    private Integer points;

    /** 实名状态：1已实名 0未实名 */
    private Integer realNameStatus;

    /** 是否黑名单：1是 0否 */
    private Integer isBlacklist;

    /** 黑名单原因 */
    private String blacklistReason;

    /** 加入/移出黑名单时间 */
    private LocalDateTime blacklistTime;

    /** 黑名单操作人ID（sys_user.id） */
    private Long blacklistOperatorId;

    /** 最近入园时间 */
    private LocalDateTime lastEnterTime;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
