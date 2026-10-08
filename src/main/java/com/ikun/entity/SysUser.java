package com.ikun.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ikun.handler.MybatisEncryptTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 后台用户表
 *
 * <p>敏感字段处理策略：</p>
 * <ul>
 *   <li>{@code password}：BCrypt 单向哈希存储，加密不可逆；</li>
 *   <li>{@code phone}：AES-256-GCM 加密存储，由 TypeHandler 读写自动加解密，数据库内不落明文；</li>
 *   <li>{@code phoneHash}：手机号 SHA-256 加盐摘要（不可逆），供加密字段的精确查询与唯一性校验。</li>
 * </ul>
 *
 * <p>{@code autoResultMap = true} 是必需的，否则查询结果不会走自定义 TypeHandler，手机号会返回密文。</p>
 *
 * @author smart-scenic
 */
@Data
@TableName(value = "sys_user", autoResultMap = true)
public class SysUser implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号 */
    private String username;

    /** 登录密码（BCrypt 单向哈希，不可逆） */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 手机号（AES-256-GCM 密文存储，读写自动加解密） */
    @TableField(typeHandler = MybatisEncryptTypeHandler.class)
    private String phone;

    /** 手机号 SHA-256 加盐摘要，用于精确检索与唯一性校验 */
    private String phoneHash;

    /** 头像 */
    private String avatar;

    /** 角色ID */
    private Long roleId;

    /** 所属景区ID，NULL 表示全部景区 */
    private Long scenicId;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 密码最近修改时间，用于提醒用户定期更换密码 */
    private LocalDateTime pwdUpdateTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
