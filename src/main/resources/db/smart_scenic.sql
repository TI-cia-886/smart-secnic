-- ============================================================================
--  智能AI景区管理系统  数据库脚本
--  数据库：smart_scenic        字符集：utf8mb4        引擎：InnoDB
--  说明：MySQL 8.0+，直接执行本脚本即可完成建库、建表与初始化数据
-- ============================================================================

DROP DATABASE IF EXISTS `smart_scenic`;
CREATE DATABASE `smart_scenic` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `smart_scenic`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 一、系统管理模块（RBAC）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 后台用户表（PC后台 / 管理端移动版 登录账号）
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_user` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`        VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 单向哈希，不可逆）',
    `real_name`       VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    `phone`           VARCHAR(255)          DEFAULT NULL COMMENT '手机号（AES-256-GCM 加密存储，不落明文）',
    `phone_hash`      CHAR(64)              DEFAULT NULL COMMENT '手机号 SHA-256 加盐摘要（不可逆），用于精确检索',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `role_id`         BIGINT       NOT NULL COMMENT '角色ID，关联 sys_role.id',
    `scenic_id`       BIGINT                DEFAULT NULL COMMENT '所属景区ID，NULL 表示全部景区',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    `pwd_update_time` DATETIME              DEFAULT NULL COMMENT '密码最近修改时间',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone_hash` (`phone_hash`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='后台用户表';

-- ---------------------------------------------------------------------------
-- 2. 角色表
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_role` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_name`   VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_code`   VARCHAR(50) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(255)         DEFAULT NULL COMMENT '角色说明',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

-- ---------------------------------------------------------------------------
-- 3. 权限表（菜单 + 按钮，树形结构）
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_permission` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '父级ID，0 为顶级',
    `perm_name`   VARCHAR(50) NOT NULL COMMENT '权限名称',
    `perm_code`   VARCHAR(80)          DEFAULT NULL COMMENT '权限标识，如 scenic:area:list',
    `perm_type`   TINYINT     NOT NULL DEFAULT 1 COMMENT '类型：1菜单 2按钮',
    `path`        VARCHAR(120)         DEFAULT NULL COMMENT '前端路由地址',
    `icon`        VARCHAR(60)          DEFAULT NULL COMMENT '菜单图标',
    `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序值',
    `status`      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='权限表';

-- ---------------------------------------------------------------------------
-- 4. 角色-权限关联表
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_role_permission` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联表';

-- ---------------------------------------------------------------------------
-- 5. 登录日志表
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_login_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT                DEFAULT NULL COMMENT '用户ID，登录失败时可能为空',
    `username`   VARCHAR(50)           DEFAULT NULL COMMENT '登录账号',
    `login_type` VARCHAR(20)  NOT NULL DEFAULT 'PC' COMMENT '来源：PC后台 MANAGER管理端移动版 MINI_PROGRAM游客小程序',
    `ip`         VARCHAR(64)           DEFAULT NULL COMMENT '登录IP',
    `location`   VARCHAR(100)          DEFAULT NULL COMMENT '归属地（可接第三方IP库）',
    `browser`    VARCHAR(100)          DEFAULT NULL COMMENT '浏览器',
    `os`         VARCHAR(100)          DEFAULT NULL COMMENT '操作系统',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    `msg`        VARCHAR(255)          DEFAULT NULL COMMENT '提示信息 / 失败原因',
    `login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='登录日志表';

-- ---------------------------------------------------------------------------
-- 6. 操作日志表（由 AOP 切面自动写入）
-- ---------------------------------------------------------------------------
CREATE TABLE `sys_oper_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`          VARCHAR(80)           DEFAULT NULL COMMENT '模块标题，如「账号管理」',
    `business_type`  VARCHAR(20)           DEFAULT NULL COMMENT '业务类型：INSERT新增 UPDATE修改 DELETE删除 EXPORT导出 IMPORT导入 GRANT授权 OTHER其它',
    `method`         VARCHAR(200)          DEFAULT NULL COMMENT '方法全路径',
    `request_method` VARCHAR(10)           DEFAULT NULL COMMENT 'HTTP 方法：GET/POST/PUT/DELETE',
    `oper_url`       VARCHAR(255)          DEFAULT NULL COMMENT '请求地址',
    `oper_ip`        VARCHAR(64)           DEFAULT NULL COMMENT '操作IP',
    `oper_param`     TEXT                  COMMENT '请求参数（JSON，敏感字段已脱敏）',
    `json_result`    TEXT                  COMMENT '返回结果（JSON）',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    `error_msg`      VARCHAR(2000)         DEFAULT NULL COMMENT '异常信息',
    `cost_time`      BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `operator_id`    BIGINT                DEFAULT NULL COMMENT '操作人ID',
    `operator_name`  VARCHAR(50)           DEFAULT NULL COMMENT '操作人账号',
    `scenic_id`      BIGINT                DEFAULT NULL COMMENT '操作时所在景区，便于按景区隔离查询',
    `oper_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_business_type` (`business_type`),
    KEY `idx_oper_time` (`oper_time`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';

-- ============================================================================
-- 二、景区运营模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 7. 景区表
-- ---------------------------------------------------------------------------
CREATE TABLE `scenic_area` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_code`    VARCHAR(30)  NOT NULL COMMENT '景区编码，如 SC-001',
    `scenic_name`    VARCHAR(80)  NOT NULL COMMENT '景区名称',
    `level`          VARCHAR(10)           DEFAULT NULL COMMENT '景区等级：5A/4A/3A',
    `province`       VARCHAR(30)           DEFAULT NULL COMMENT '省份',
    `city`           VARCHAR(30)           DEFAULT NULL COMMENT '城市',
    `address`        VARCHAR(255)          DEFAULT NULL COMMENT '详细地址',
    `longitude`      DECIMAL(10, 6)        DEFAULT NULL COMMENT '经度',
    `latitude`       DECIMAL(10, 6)        DEFAULT NULL COMMENT '纬度',
    `daily_capacity` INT          NOT NULL DEFAULT 0 COMMENT '日承载量（人）',
    `manager`        VARCHAR(50)           DEFAULT NULL COMMENT '负责人',
    `phone`          VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
    `cover_img`      VARCHAR(255)          DEFAULT NULL COMMENT '封面图',
    `description`    VARCHAR(500)          DEFAULT NULL COMMENT '景区简介',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'ENABLE' COMMENT '状态：ENABLE启用 DISABLE停用',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scenic_code` (`scenic_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='景区表';

-- ---------------------------------------------------------------------------
-- 8. 景点表
-- ---------------------------------------------------------------------------
CREATE TABLE `scenic_spot` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `spot_code`        VARCHAR(30)  NOT NULL COMMENT '景点编号，如 SP-001',
    `scenic_id`        BIGINT       NOT NULL COMMENT '所属景区ID',
    `spot_name`        VARCHAR(80)  NOT NULL COMMENT '景点名称',
    `spot_type`        VARCHAR(20)  NOT NULL DEFAULT 'NATURAL' COMMENT '类型：NATURAL自然景观 CULTURAL人文景观 PLAY游乐项目',
    `suggest_duration` INT                   DEFAULT NULL COMMENT '建议游玩时长（分钟）',
    `instant_capacity` INT          NOT NULL DEFAULT 0 COMMENT '瞬时承载量（人）',
    `current_count`    INT          NOT NULL DEFAULT 0 COMMENT '当前在园人数',
    `price`            DECIMAL(10, 2)        DEFAULT 0.00 COMMENT '单独门票价（元）',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN开放 MAINTENANCE维护中 CLOSED关闭',
    `longitude`        DECIMAL(10, 6)        DEFAULT NULL COMMENT '经度',
    `latitude`         DECIMAL(10, 6)        DEFAULT NULL COMMENT '纬度',
    `cover_img`        VARCHAR(255)          DEFAULT NULL COMMENT '图片',
    `description`      VARCHAR(500)          DEFAULT NULL COMMENT '景点介绍',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spot_code` (`spot_code`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='景点表';

-- ---------------------------------------------------------------------------
-- 9. 门票类型表
-- ---------------------------------------------------------------------------
CREATE TABLE `ticket_type` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`      BIGINT        NOT NULL COMMENT '所属景区ID',
    `ticket_name`    VARCHAR(60)   NOT NULL COMMENT '票种名称，如 成人票',
    `ticket_type`    VARCHAR(20)   NOT NULL DEFAULT 'ADULT' COMMENT '票种编码：ADULT成人 CHILD儿童 STUDENT学生 SENIOR老人 FAMILY亲子 PACKAGE联票 GROUP团队',
    `price`          DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '售价（元）',
    `original_price` DECIMAL(10, 2)          DEFAULT NULL COMMENT '原价（元）',
    `stock`          INT           NOT NULL DEFAULT 0 COMMENT '每日库存',
    `description`    VARCHAR(255)           DEFAULT NULL COMMENT '票种说明',
    `status`         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='门票类型表';

-- ---------------------------------------------------------------------------
-- 10. 门票日期库存表
--    说明：ticket_type.stock 只是「每日库存总量」的默认值，
--          真正按游玩日期扣减库存需要本表；下单锁库存、取消回滚、超卖校验都依赖它。
-- ---------------------------------------------------------------------------
CREATE TABLE `ticket_stock` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`      BIGINT   NOT NULL COMMENT '景区ID',
    `ticket_type_id` BIGINT   NOT NULL COMMENT '票种ID',
    `stock_date`     DATE     NOT NULL COMMENT '库存日期（游玩日期）',
    `total_stock`    INT      NOT NULL DEFAULT 0 COMMENT '当日总库存，默认取 ticket_type.stock',
    `sold_count`     INT      NOT NULL DEFAULT 0 COMMENT '已售数量（已支付）',
    `locked_count`   INT      NOT NULL DEFAULT 0 COMMENT '锁定数量（已下单未支付）',
    `status`         TINYINT  NOT NULL DEFAULT 1 COMMENT '状态：1开放 0停售',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_date` (`ticket_type_id`, `stock_date`),
    KEY `idx_scenic_date` (`scenic_id`, `stock_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='门票日期库存表';

-- ---------------------------------------------------------------------------
-- 11. 门票订单表
-- ---------------------------------------------------------------------------
CREATE TABLE `ticket_order` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`       VARCHAR(40)   NOT NULL COMMENT '订单号',
    `tourist_id`     BIGINT        NOT NULL COMMENT '游客ID',
    `scenic_id`      BIGINT        NOT NULL COMMENT '景区ID',
    `ticket_type_id` BIGINT        NOT NULL COMMENT '票种ID',
    `ticket_name`    VARCHAR(60)   NOT NULL COMMENT '票种名称（冗余）',
    `quantity`       INT           NOT NULL DEFAULT 1 COMMENT '购票数量',
    `unit_price`     DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '单价（元）',
    `total_amount`   DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '订单总额（元）',
    `play_date`      DATE          NOT NULL COMMENT '游玩日期',
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAY' COMMENT '状态：PENDING_PAY待付款 PAID已支付 VERIFIED已核销 REFUNDING已申请退票待审核 REFUNDED已退款 CANCELLED已取消',
    `channel`        VARCHAR(20)   NOT NULL DEFAULT 'MINI_PROGRAM' COMMENT '购票渠道：MINI_PROGRAM小程序 OTA第三方 WINDOW现场窗口 AGENCY旅行社',
    `expire_time`    DATETIME               DEFAULT NULL COMMENT '支付截止时间，超时由定时任务自动取消',
    `pay_time`       DATETIME               DEFAULT NULL COMMENT '支付时间',
    `verify_time`    DATETIME               DEFAULT NULL COMMENT '核销时间',
    `refund_time`    DATETIME               DEFAULT NULL COMMENT '退款时间',
    `refund_reason`  VARCHAR(255)           DEFAULT NULL COMMENT '退票原因（游客填写）',
    `refund_apply_time`   DATETIME         DEFAULT NULL COMMENT '退票申请时间',
    `refund_audit_by`     BIGINT           DEFAULT NULL COMMENT '退票审核人ID（sys_user.id）',
    `refund_audit_time`   DATETIME         DEFAULT NULL COMMENT '退票审核时间',
    `refund_audit_remark` VARCHAR(255)     DEFAULT NULL COMMENT '退票审核意见（驳回时必填）',
    `contact_name`   VARCHAR(50)            DEFAULT NULL COMMENT '取票人姓名',
    `contact_phone`  VARCHAR(20)            DEFAULT NULL COMMENT '取票人手机号',
    `qr_code`        VARCHAR(128)           DEFAULT NULL COMMENT '电子票二维码内容（订单号+签名），前端据此渲染二维码',
    `remark`         VARCHAR(255)           DEFAULT NULL COMMENT '备注',
    `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tourist_id` (`tourist_id`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_play_date` (`play_date`),
    KEY `idx_status` (`status`),
    KEY `idx_scenic_status` (`scenic_id`, `status`),
    KEY `idx_scenic_play_date` (`scenic_id`, `play_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='门票订单表';

-- ---------------------------------------------------------------------------
-- 12. 检票核验记录表
-- ---------------------------------------------------------------------------
CREATE TABLE `checkin_record` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_id`    BIGINT               DEFAULT NULL COMMENT '订单ID',
    `order_no`    VARCHAR(40)          DEFAULT NULL COMMENT '订单号',
    `scenic_id`   BIGINT      NOT NULL COMMENT '景区ID',
    `gate`        VARCHAR(50)          DEFAULT NULL COMMENT '检票口，如 南门1号闸机',
    `ticket_name` VARCHAR(60)          DEFAULT NULL COMMENT '票种名称',
    `quantity`    INT         NOT NULL DEFAULT 1 COMMENT '核验数量',
    `verify_type` VARCHAR(20) NOT NULL DEFAULT 'QRCODE' COMMENT '核验方式：QRCODE二维码 IDCARD身份证 FACE人脸识别 MANUAL人工',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS成功 FAIL失败',
    `fail_reason` VARCHAR(255)         DEFAULT NULL COMMENT '失败原因',
    `verify_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核验时间',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_verify_time` (`verify_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='检票核验记录表';

-- ---------------------------------------------------------------------------
-- 13. 公告资讯表
-- ---------------------------------------------------------------------------
CREATE TABLE `announcement` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`    BIGINT               DEFAULT NULL COMMENT '所属景区ID，NULL 表示全平台',
    `title`        VARCHAR(120) NOT NULL COMMENT '标题',
    `content`      TEXT                 COMMENT '正文内容',
    `type`         VARCHAR(20)  NOT NULL DEFAULT 'NOTICE' COMMENT '类型：NOTICE公告 WARNING预警 ACTIVITY活动',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT草稿 PUBLISHED已发布 OFFLINE已下线',
    `is_top`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：1是 0否',
    `ai_generated` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否由AI生成草稿：1是 0否',
    `ai_prompt`    VARCHAR(500)          DEFAULT NULL COMMENT 'AI生成时使用的提示词，便于复现',
    `publisher_id` BIGINT                DEFAULT NULL COMMENT '发布人ID',
    `publish_time` DATETIME              DEFAULT NULL COMMENT '发布时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_scenic_status` (`scenic_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='公告资讯表';

-- ---------------------------------------------------------------------------
-- 14. 停车场表
-- ---------------------------------------------------------------------------
CREATE TABLE `parking_lot` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`   BIGINT      NOT NULL COMMENT '所属景区ID',
    `lot_name`    VARCHAR(60) NOT NULL COMMENT '停车场名称',
    `total_space` INT         NOT NULL DEFAULT 0 COMMENT '总车位数',
    `free_space`  INT         NOT NULL DEFAULT 0 COMMENT '剩余车位',
    `status`      VARCHAR(20) NOT NULL DEFAULT 'FREE' COMMENT '状态：FREE空闲 BUSY紧张 FULL已满',
    `fee_rule`    VARCHAR(120)         DEFAULT NULL COMMENT '收费标准',
    `longitude`   DECIMAL(10, 6)        DEFAULT NULL COMMENT '经度',
    `latitude`    DECIMAL(10, 6)        DEFAULT NULL COMMENT '纬度',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='停车场表';

-- ============================================================================
-- 三、游客与客服模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 15. 游客信息表（小程序用户）
-- ---------------------------------------------------------------------------
CREATE TABLE `tourist` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tourist_no`       VARCHAR(30) NOT NULL COMMENT '游客编号，如 T20250001',
    `openid`           VARCHAR(64)          DEFAULT NULL COMMENT '微信 openid',
    `register_scenic_id` BIGINT            DEFAULT NULL COMMENT '注册来源景区ID（游客从哪个景区入口注册），用于后台按景区过滤',
    `real_name`        VARCHAR(50) NOT NULL COMMENT '姓名',
    `phone`            VARCHAR(20) NOT NULL COMMENT '手机号',
    `phone_hash`       CHAR(64)             DEFAULT NULL COMMENT '手机号SHA-256加盐摘要，用于精确检索与唯一性校验',
    `password`         VARCHAR(100)         DEFAULT NULL COMMENT '登录密码（BCrypt单向哈希，不可逆）',
    `id_card`          VARCHAR(30)          DEFAULT NULL COMMENT '证件号码',
    `gender`           TINYINT     NOT NULL DEFAULT 0 COMMENT '性别：1男 2女 0未知',
    `avatar`           VARCHAR(255)         DEFAULT NULL COMMENT '头像',
    `source`           VARCHAR(20) NOT NULL DEFAULT 'MINI_PROGRAM' COMMENT '注册来源：MINI_PROGRAM小程序 OTA第三方 WINDOW现场',
    `member_level`     VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '会员等级：NORMAL普通 SILVER白银 GOLD黄金 DIAMOND钻石',
    `points`           INT         NOT NULL DEFAULT 0 COMMENT '积分',
    `real_name_status` TINYINT     NOT NULL DEFAULT 0 COMMENT '实名状态：1已实名 0未实名',
    `is_blacklist`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否黑名单：1是 0否',
    `blacklist_reason` VARCHAR(255)         DEFAULT NULL COMMENT '黑名单原因',
    `blacklist_time`   DATETIME             DEFAULT NULL COMMENT '加入/移出黑名单时间',
    `blacklist_operator_id` BIGINT          DEFAULT NULL COMMENT '黑名单操作人ID（sys_user.id）',
    `last_enter_time`  DATETIME             DEFAULT NULL COMMENT '最近入园时间',
    `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    `remark`           VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tourist_no` (`tourist_no`),
    UNIQUE KEY `uk_tourist_phone_hash` (`phone_hash`),
    KEY `idx_phone` (`phone`),
    KEY `idx_real_name` (`real_name`),
    KEY `idx_register_scenic` (`register_scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='游客信息表';

-- ---------------------------------------------------------------------------
-- 16. 投诉工单表
-- ---------------------------------------------------------------------------
CREATE TABLE `complaint` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket_no`    VARCHAR(30)  NOT NULL COMMENT '工单编号，如 TK-20250531-001',
    `scenic_id`    BIGINT       NOT NULL COMMENT '景区ID',
    `tourist_id`   BIGINT                DEFAULT NULL COMMENT '游客ID（可为空，未注册游客）',
    `tourist_name` VARCHAR(50)           DEFAULT NULL COMMENT '游客姓名',
    `phone`        VARCHAR(20)           DEFAULT NULL COMMENT '联系电话',
    `order_no`     VARCHAR(40)           DEFAULT NULL COMMENT '关联订单号',
    `title`        VARCHAR(120) NOT NULL COMMENT '工单标题',
    `content`      VARCHAR(1000)         DEFAULT NULL COMMENT '问题描述',
    `type`         VARCHAR(20)  NOT NULL DEFAULT 'COMPLAINT' COMMENT '类型：COMPLAINT投诉 SUGGESTION建议 CONSULT咨询',
    `priority`     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：NORMAL普通 URGENT紧急',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待处理 PROCESSING处理中 CLOSED已完结',
    `sentiment`    VARCHAR(20)           DEFAULT NULL COMMENT 'AI情感倾向：POSITIVE积极 NEUTRAL中性 NEGATIVE消极',
    `sentiment_score` DECIMAL(4, 3)      DEFAULT NULL COMMENT 'AI情感得分，区间 -1.000 ~ 1.000',
    `sentiment_time`  DATETIME           DEFAULT NULL COMMENT '情感分析时间',
    `handler_id`   BIGINT                DEFAULT NULL COMMENT '处理人ID（sys_user.id）',
    `handle_time`  DATETIME              DEFAULT NULL COMMENT '处理完成时间',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_status` (`status`),
    KEY `idx_scenic_status` (`scenic_id`, `status`),
    KEY `idx_sentiment` (`sentiment`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='投诉工单表';

-- ---------------------------------------------------------------------------
-- 17. 工单回复表（游客留言 / 客服回复 / AI 自动回复）
-- ---------------------------------------------------------------------------
CREATE TABLE `complaint_reply` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `complaint_id` BIGINT       NOT NULL COMMENT '工单ID',
    `reply_type`   VARCHAR(20)  NOT NULL DEFAULT 'STAFF' COMMENT '回复方：TOURIST游客 STAFF客服 AI智能助手',
    `content`      VARCHAR(1000) NOT NULL COMMENT '回复内容',
    `reply_by`     VARCHAR(50)           DEFAULT NULL COMMENT '回复人名称',
    `sentiment`    VARCHAR(20)           DEFAULT NULL COMMENT 'AI情感倾向：POSITIVE积极 NEUTRAL中性 NEGATIVE消极',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '回复时间',
    PRIMARY KEY (`id`),
    KEY `idx_complaint_id` (`complaint_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='工单回复表';

-- ============================================================================
-- 四、客流统计与预警模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 18. 客流统计表（按小时）
-- ---------------------------------------------------------------------------
CREATE TABLE `passenger_flow` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`     BIGINT   NOT NULL COMMENT '景区ID',
    `stat_date`     DATE     NOT NULL COMMENT '统计日期',
    `stat_hour`     TINYINT  NOT NULL COMMENT '统计时段（小时 0-23）',
    `enter_count`   INT      NOT NULL DEFAULT 0 COMMENT '入园人数',
    `leave_count`   INT      NOT NULL DEFAULT 0 COMMENT '出园人数',
    `current_count` INT      NOT NULL DEFAULT 0 COMMENT '在园人数',
    `capacity`      INT               DEFAULT 0 COMMENT '该时段承载量',
    `warning_level` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '预警级别：NORMAL正常 WARNING预警 DANGER超载',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scenic_date_hour` (`scenic_id`, `stat_date`, `stat_hour`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='客流统计表';

-- ---------------------------------------------------------------------------
-- 19. 超载预警记录表
-- ---------------------------------------------------------------------------
CREATE TABLE `flow_warning` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`     BIGINT      NOT NULL COMMENT '景区ID',
    `spot_id`       BIGINT               DEFAULT NULL COMMENT '景点ID（景区级预警为空）',
    `spot_name`     VARCHAR(80)          DEFAULT NULL COMMENT '景点名称',
    `current_count` INT         NOT NULL DEFAULT 0 COMMENT '当前人数',
    `capacity`      INT         NOT NULL DEFAULT 0 COMMENT '承载量',
    `warning_level` VARCHAR(20) NOT NULL DEFAULT 'WARNING' COMMENT '级别：WARNING预警 DANGER超载',
    `status`        VARCHAR(20) NOT NULL DEFAULT 'UNHANDLED' COMMENT '状态：UNHANDLED未处理 HANDLED已处理',
    `handle_remark` VARCHAR(255)         DEFAULT NULL COMMENT '处置说明',
    `ai_plan`       TEXT                          COMMENT 'AI生成的客流疏导预案全文',
    `ai_plan_time`  DATETIME             DEFAULT NULL COMMENT 'AI预案生成时间',
    `handler_id`    BIGINT               DEFAULT NULL COMMENT '处理人ID',
    `handle_time`   DATETIME             DEFAULT NULL COMMENT '处理时间',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预警时间',
    `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='超载预警记录表';

-- ============================================================================
-- 五、AI 智能模块
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 20. AI 知识库
-- ---------------------------------------------------------------------------
CREATE TABLE `ai_knowledge` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`   BIGINT                DEFAULT NULL COMMENT '所属景区ID，NULL 表示通用',
    `category`    VARCHAR(30)  NOT NULL DEFAULT 'COMMON' COMMENT '分类：COMMON通用 TICKET票务 ROUTE路线 SERVICE服务 SAFETY安全',
    `question`    VARCHAR(255) NOT NULL COMMENT '标准问题',
    `answer`      VARCHAR(1000) NOT NULL COMMENT '标准答案',
    `keywords`    VARCHAR(255)          DEFAULT NULL COMMENT '关键词，逗号分隔',
    `embedding`   TEXT                           COMMENT '问题向量化结果（JSON 数组），用于语义检索；不使用向量检索时留空',
    `embedding_model` VARCHAR(60)         DEFAULT NULL COMMENT '生成该向量的模型名，如 nomic-embed-text',
    `hit_count`   INT          NOT NULL DEFAULT 0 COMMENT '命中次数',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI知识库';

-- ---------------------------------------------------------------------------
-- 21. AI 调用日志（智能问答/情感分析/公告草稿/批量归纳/疏导预案/路线推荐）
-- ---------------------------------------------------------------------------
CREATE TABLE `ai_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`      BIGINT               DEFAULT NULL COMMENT '景区ID',
    `module`         VARCHAR(30) NOT NULL COMMENT '功能模块：KNOWLEDGE知识库问答 SENTIMENT情感分析 ANNOUNCEMENT公告草稿 SUMMARY工单批量归纳 FLOW_PLAN客流疏导预案 ROUTE路线推荐',
    `input_summary`  VARCHAR(500)         DEFAULT NULL COMMENT '输入摘要',
    `output_summary` VARCHAR(500)         DEFAULT NULL COMMENT '输出摘要',
    `duration`       INT         NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS成功 FAIL失败',
    `error_msg`      VARCHAR(500)         DEFAULT NULL COMMENT '错误信息',
    `operator_id`    BIGINT               DEFAULT NULL COMMENT '操作人ID（后台）',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (`id`),
    KEY `idx_scenic_id` (`scenic_id`),
    KEY `idx_module` (`module`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI调用日志表';

-- ---------------------------------------------------------------------------
-- 22. AI 会话记录（游客小程序智能客服）
-- ---------------------------------------------------------------------------
CREATE TABLE `ai_chat` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  VARCHAR(64) NOT NULL COMMENT '会话ID',
    `tourist_id`  BIGINT               DEFAULT NULL COMMENT '游客ID',
    `scenic_id`   BIGINT               DEFAULT NULL COMMENT '景区ID',
    `role`        VARCHAR(20) NOT NULL COMMENT '角色：user游客 assistant智能助手',
    `content`     VARCHAR(2000) NOT NULL COMMENT '消息内容',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_tourist_id` (`tourist_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='AI会话记录表';

-- ============================================================================
-- 六、初始化数据
-- ============================================================================

-- ---- 景区 ----
INSERT INTO `scenic_area` (`id`, `scenic_code`, `scenic_name`, `level`, `province`, `city`, `address`, `longitude`, `latitude`, `daily_capacity`, `manager`, `phone`, `description`, `status`) VALUES
(1, 'SC-001', '西湖风景区', '5A', '浙江省', '杭州市', '浙江省杭州市西湖区龙井路1号', 120.140000, 30.250000, 80000, '李秀英', '0571-87179617', '世界文化遗产，以西湖十景闻名，是杭州的城市名片。', 'ENABLE'),
(2, 'SC-002', '黄山风景区', '5A', '安徽省', '黄山市', '安徽省黄山市黄山区汤口镇', 118.166000, 30.133000, 50000, '赵敏', '0559-5561111', '世界文化与自然双遗产，奇松、怪石、云海、温泉四绝。', 'ENABLE'),
(3, 'SC-003', '九寨沟', '5A', '四川省', '阿坝州', '四川省阿坝藏族羌族自治州九寨沟县', 103.920000, 33.260000, 41000, '周明', '0837-7739753', '以翠海、叠瀑、彩林、雪峰、藏情五绝著称的自然保护区。', 'ENABLE'),
(4, 'SC-004', '千岛湖', '4A', '浙江省', '杭州市', '浙江省杭州市淳安县千岛湖镇', 119.040000, 29.600000, 35000, '吴海燕', '0571-24817707', '因湖中拥有1078座岛屿而得名的人工湖景区。', 'ENABLE'),
(5, 'SC-005', '乌镇古镇', '5A', '浙江省', '嘉兴市', '浙江省嘉兴市桐乡市乌镇镇石佛南路18号', 120.490000, 30.740000, 60000, '郑伟', '0573-88731088', '典型的江南水乡古镇，以东西栅景区闻名。', 'DISABLE');

-- ---- 角色 ----
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `description`, `status`) VALUES
(1, '超级管理员', 'SUPER_ADMIN', '拥有系统全部权限', 1),
(2, '景区管理员', 'SCENIC_ADMIN', '负责本景区的基础信息与运营管理', 1),
(3, '运营人员', 'OPERATOR', '负责订单、票务与公告运营', 1),
(4, '客服人员', 'SERVICE', '负责投诉工单处理与回复', 1),
(5, 'AI训练师', 'AI_TRAINER', '负责AI知识库维护与模型调优', 1);

-- ---- 权限（菜单树 + 按钮） ----
INSERT INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `icon`, `sort`, `status`) VALUES
(1,  0, '数据概览',       'dashboard:view',           1, '/dashboard',       'dashboard', 1, 1),
(2,  0, '账号管理',       'system:user:list',         1, '/system/user',     'user',      2, 1),
(3,  2, '新增账号',       'system:user:add',          2, NULL,               NULL,        1, 1),
(4,  2, '编辑账号',       'system:user:edit',         2, NULL,               NULL,        2, 1),
(5,  2, '删除账号',       'system:user:delete',       2, NULL,               NULL,        3, 1),
(6,  2, '重置密码',       'system:user:reset',        2, NULL,               NULL,        4, 1),
(7,  0, '角色权限管理',   'system:role:list',         1, '/system/role',     'lock',      3, 1),
(8,  7, '分配权限',       'system:role:assign',       2, NULL,               NULL,        1, 1),
(9,  0, '景区管理',       'scenic:area:list',         1, '/scenic/area',     'scenic',    4, 1),
(10, 9, '新增景区',       'scenic:area:add',          2, NULL,               NULL,        1, 1),
(11, 9, '编辑景区',       'scenic:area:edit',         2, NULL,               NULL,        2, 1),
(12, 0, '基础数据',       'scenic:data:list',         1, '/scenic/data',     'database',  5, 1),
(13, 0, '游客信息管理',   'tourist:list',             1, '/tourist',         'people',    6, 1),
(14, 13, '黑名单设置',    'tourist:blacklist',        2, NULL,               NULL,        1, 1),
(15, 0, '订单预约管理',   'order:list',               1, '/order',           'orders',    7, 1),
(16, 15, '订单核销',      'order:verify',             2, NULL,               NULL,        1, 1),
(17, 15, '订单退票',      'order:refund',             2, NULL,               NULL,        2, 1),
(18, 0, '客流统计',       'flow:list',                1, '/flow',            'chart',     8, 1),
(19, 0, '投诉工单管理',   'complaint:list',           1, '/complaint',       'message',   9, 1),
(20, 19, '工单回复',      'complaint:reply',          2, NULL,               NULL,        1, 1),
(21, 19, '工单转派',      'complaint:assign',         2, NULL,               NULL,        2, 1),
(22, 0, 'AI智能管理',     'ai:manage:list',           1, '/ai',              'ai',       10, 1),
(23, 22, '知识库维护',    'ai:knowledge:edit',        2, NULL,               NULL,        1, 1),
(24, 0, '数据统计报表',   'report:view',              1, '/report',          'report',   11, 1),
(25, 24, '导出报表',      'report:export',            2, NULL,               NULL,        1, 1),
-- 基础数据（12）下的四个二级菜单
(26, 12, '景点管理',       'scenic:spot:list',         1, '/scenic/spot',         'map-pin',  1, 1),
(27, 12, '门票类型管理',   'scenic:ticket:list',       1, '/scenic/ticket',       'ticket',   2, 1),
(28, 12, '公告资讯管理',   'scenic:announcement:list', 1, '/scenic/announcement', 'notice',   3, 1),
(29, 12, '停车场点位管理', 'scenic:parking:list',      1, '/scenic/parking',      'parking',  4, 1),
-- 游客信息管理（13）
(30, 13, '注册统计图表',   'tourist:stat',             2, NULL,                   NULL,       2, 1),
-- 订单预约管理（15）
(31, 15, '订单导出',       'order:export',             2, NULL,                   NULL,       3, 1),
-- 客流统计（18）
(32, 18, '客流数据报表',   'flow:report',              2, NULL,                   NULL,       1, 1),
(33, 18, '预警处理',       'flow:warning:handle',      2, NULL,                   NULL,       2, 1),
(34, 18, '检票核验记录',   'flow:checkin:list',        2, NULL,                   NULL,       3, 1),
-- 投诉工单管理（19）
(35, 19, '情感分析',       'complaint:sentiment',      2, NULL,                   NULL,       3, 1),
(36, 19, '批量归纳汇总',   'complaint:summary',        2, NULL,                   NULL,       4, 1),
-- AI 智能管理（22）
(37, 22, '公告草稿生成',   'ai:announcement:draft',    2, NULL,                   NULL,       2, 1),
(38, 22, '疏导预案生成',   'ai:flow:plan',             2, NULL,                   NULL,       3, 1),
(39, 22, 'AI调用日志',     'ai:log:list',              2, NULL,                   NULL,       4, 1),
(40, 22, '智能客服会话',   'ai:chat:list',             2, NULL,                   NULL,       5, 1),
-- 数据统计报表（24）
(41, 24, '门票销售统计',   'report:ticket',            2, NULL,                   NULL,       2, 1),
(42, 24, '营收统计图表',   'report:revenue',           2, NULL,                   NULL,       3, 1),
-- 系统管理：日志
(43, 0,  '登录日志',       'system:loginlog:list',     1, '/system/login-log',    'login',    12, 1),
(44, 0,  '操作日志',       'system:operlog:list',      1, '/system/oper-log',     'log',      13, 1),
-- 景区管理（9）按钮：删除景区（仅超级管理员）
(45, 9,  '删除景区',       'scenic:area:delete',       2, NULL,                   NULL,       3, 1);

-- ---- 后台用户 ----
-- 说明：密码以历史 MD5 形式给出（明文统一为 123456），
--       各账号下次登录成功后会由 PasswordUtil 自动升级为 BCrypt；
--       手机号此处保持明文，应用启动时由 DataEncryptionMigrationRunner
--       自动加密为 AES-256-GCM 密文，并回填 phone_hash 摘要列。
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `role_id`, `scenic_id`, `status`, `last_login_time`) VALUES
(1, 'admin',       'e10adc3949ba59abbe56e057f20f883e', '张建国', '13800138000', 1, NULL, 1, NOW()),
(2, 'xihu01',      'e10adc3949ba59abbe56e057f20f883e', '李秀英', '13800001111', 2, 1,    1, NOW()),
(3, 'xihu02',      'e10adc3949ba59abbe56e057f20f883e', '王海涛', '13800002222', 3, 1,    1, NOW()),
(4, 'huangshan01', 'e10adc3949ba59abbe56e057f20f883e', '赵敏',   '13800003333', 2, 2,    1, NULL),
(5, 'service03',   'e10adc3949ba59abbe56e057f20f883e', '刘芳',   '13800004444', 4, 1,    1, NULL),
(6, 'admin02',     'e10adc3949ba59abbe56e057f20f883e', '陈志强', '13800005555', 1, NULL, 0, NULL);

-- ---- 角色权限：超级管理员拥有全部权限 ----
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`;

-- ---- 角色权限：景区管理员（本景区全业务，但「景区总管理」9/10/11 仅超级管理员可见） ----
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8),
(2, 12), (2, 26), (2, 27), (2, 28), (2, 29),
(2, 13), (2, 14), (2, 30),
(2, 15), (2, 16), (2, 17), (2, 31),
(2, 18), (2, 32), (2, 33), (2, 34),
(2, 19), (2, 20), (2, 21), (2, 35), (2, 36),
(2, 22), (2, 23), (2, 37), (2, 38), (2, 39), (2, 40),
(2, 24), (2, 25), (2, 41), (2, 42),
(2, 43), (2, 44);

-- ---- 角色权限：运营人员（票务、公告、客流） ----
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(3, 1),
(3, 12), (3, 26), (3, 27), (3, 28), (3, 29),
(3, 15), (3, 16), (3, 17), (3, 31),
(3, 18), (3, 32), (3, 33), (3, 34),
(3, 24), (3, 25), (3, 41), (3, 42);

-- ---- 角色权限：客服人员（游客、工单） ----
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(4, 1),
(4, 13), (4, 14), (4, 30),
(4, 15), (4, 17),
(4, 19), (4, 20), (4, 21), (4, 35), (4, 36);

-- ---- 角色权限：AI训练师（知识库与 AI 能力） ----
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(5, 1),
(5, 19), (5, 35), (5, 36),
(5, 22), (5, 23), (5, 37), (5, 38), (5, 39), (5, 40);

-- ---- 景点（以西湖、黄山为例） ----
INSERT INTO `scenic_spot` (`id`, `spot_code`, `scenic_id`, `spot_name`, `spot_type`, `suggest_duration`, `instant_capacity`, `current_count`, `price`, `status`, `description`) VALUES
(1, 'SP-001', 1, '断桥残雪', 'NATURAL',  40, 2000, 1500, 0.00,  'OPEN',        '西湖十景之一，白堤东端，冬日雪景闻名。'),
(2, 'SP-002', 1, '苏堤春晓', 'NATURAL',  60, 3000, 2300, 0.00,  'OPEN',        '贯穿西湖南北的长堤，春日景色最佳。'),
(3, 'SP-003', 1, '三潭印月', 'NATURAL',  50, 1500, 800,  55.00, 'OPEN',        '西湖中最大岛屿，一元纸币背面图案。'),
(4, 'SP-004', 1, '雷峰塔',   'CULTURAL', 60, 1200, 1180, 40.00, 'OPEN',        '西湖十景之一，登塔可俯瞰西湖全景。'),
(5, 'SP-005', 2, '迎客松',   'NATURAL',  30, 1800, 600,  0.00,  'OPEN',        '黄山标志性景观，树龄逾千年。'),
(6, 'SP-006', 2, '光明顶',   'NATURAL',  90, 1500, 400,  0.00,  'MAINTENANCE', '黄山第二高峰，观日出云海最佳处。');

-- ---- 门票类型 ----
INSERT INTO `ticket_type` (`id`, `scenic_id`, `ticket_name`, `ticket_type`, `price`, `original_price`, `stock`, `description`, `status`) VALUES
(1, 1, '成人票', 'ADULT',   75.00,  80.00,  5000, '适用于18-59周岁游客，凭身份证入园', 1),
(2, 1, '学生票', 'STUDENT', 38.00,  40.00,  2000, '全日制学生凭学生证购买', 1),
(3, 1, '老年票', 'SENIOR',  0.00,   0.00,   1000, '60周岁以上老人免票，需预约', 1),
(4, 1, '亲子票', 'FAMILY',  168.00, 190.00, 800,  '1名成人+1名儿童', 1),
(5, 2, '成人票', 'ADULT',   190.00, 190.00, 4000, '黄山风景区大门票', 1),
(6, 2, '联票',   'PACKAGE', 320.00, 350.00, 1000, '大门票+索道往返', 1);

-- ---- 门票日期库存（各票种 × 今天起 3 天，取票 2025-05-31 为例） ----
INSERT INTO `ticket_stock` (`scenic_id`, `ticket_type_id`, `stock_date`, `total_stock`, `sold_count`, `locked_count`, `status`) VALUES
(1, 1, '2025-05-31', 5000, 1240, 36, 1),
(1, 1, '2025-06-01', 5000, 320,  12, 1),
(1, 1, '2025-06-02', 5000, 0,    0,  1),
(1, 2, '2025-05-31', 2000, 410,  8,  1),
(1, 2, '2025-06-01', 2000, 96,   3,  1),
(1, 3, '2025-05-31', 1000, 150,  0,  1),
(1, 4, '2025-05-31', 800,  62,   4,  1),
(2, 5, '2025-05-31', 4000, 2680, 75, 1),
(2, 5, '2025-06-01', 4000, 1500, 40, 1),
(2, 6, '2025-05-31', 1000, 880,  22, 1);

-- ---- 游客 ----
INSERT INTO `tourist` (`id`, `tourist_no`, `real_name`, `phone`, `id_card`, `gender`, `source`, `member_level`, `points`, `real_name_status`, `is_blacklist`, `blacklist_reason`, `last_enter_time`, `status`) VALUES
(1, 'T20250001', '王晓明', '13812341122', '330106199001011234', 1, 'MINI_PROGRAM', 'GOLD',   3200, 1, 0, NULL, '2025-05-31 09:12:00', 1),
(2, 'T20250002', '林小红', '13912342233', '330106199502022345', 2, 'OTA',          'SILVER', 1500, 1, 0, NULL, '2025-05-30 14:20:00', 1),
(3, 'T20250003', '张大军', '13712343344', '330106198803033456', 1, 'WINDOW',       'NORMAL', 200,  1, 1, '多次恶意退票', '2025-05-28 10:05:00', 1),
(4, 'T20250004', '陈思思', '13612344455', '330106200104044567', 2, 'MINI_PROGRAM', 'NORMAL', 800,  1, 0, NULL, '2025-05-31 08:40:00', 1),
(5, 'T20250005', '刘建国', '13512345566', '330106197505055678', 1, 'OTA',          'DIAMOND',5600, 1, 1, '使用他人证件入园', '2025-05-20 16:30:00', 1);

-- ---- 门票订单 ----
INSERT INTO `ticket_order` (`id`, `order_no`, `tourist_id`, `scenic_id`, `ticket_type_id`, `ticket_name`, `quantity`, `unit_price`, `total_amount`, `play_date`, `status`, `channel`, `pay_time`, `verify_time`, `contact_name`, `contact_phone`) VALUES
(1, 'ORD20250531001', 1, 1, 1, '成人票', 2, 75.00,  150.00, '2025-05-31', 'VERIFIED',    'MINI_PROGRAM', '2025-05-30 10:12:00', '2025-05-31 09:12:00', '王晓明', '13812341122'),
(2, 'ORD20250531002', 2, 2, 5, '成人票', 1, 190.00, 190.00, '2025-06-01', 'PAID',        'OTA',          '2025-05-30 15:00:00', NULL,                  '林小红', '13912342233'),
(3, 'ORD20250531003', 4, 1, 4, '亲子票', 1, 168.00, 168.00, '2025-05-31', 'VERIFIED',    'MINI_PROGRAM', '2025-05-31 08:00:00', '2025-05-31 08:40:00', '陈思思', '13612344455'),
(4, 'ORD20250531004', 3, 1, 1, '成人票', 3, 75.00,  225.00, '2025-05-29', 'REFUNDING',   'WINDOW',       '2025-05-28 17:20:00', NULL,                  '张大军', '13712343344'),
(5, 'ORD20250531005', 1, 1, 3, '老年票', 1, 0.00,   0.00,   '2025-06-02', 'PENDING_PAY', 'MINI_PROGRAM', NULL,                  NULL,                  '王晓明', '13812341122'),
(6, 'ORD20250530008', 5, 2, 6, '联票',   2, 320.00, 640.00, '2025-05-31', 'REFUNDED',    'OTA',          '2025-05-29 09:30:00', NULL,                  '刘建国', '13512345566');

-- ---- 检票核验记录 ----
INSERT INTO `checkin_record` (`order_id`, `order_no`, `scenic_id`, `gate`, `ticket_name`, `quantity`, `verify_type`, `status`, `fail_reason`, `verify_time`) VALUES
(1, 'ORD20250531001', 1, '南门1号闸机', '成人票', 1, 'QRCODE', 'SUCCESS', NULL,          '2025-05-31 09:00:00'),
(1, 'ORD20250531001', 1, '南门1号闸机', '成人票', 1, 'QRCODE', 'SUCCESS', NULL,          '2025-05-31 09:12:00'),
(3, 'ORD20250531003', 1, '北门2号闸机', '亲子票', 1, 'IDCARD', 'SUCCESS', NULL,          '2025-05-31 08:40:00'),
(4, 'ORD20250531004', 1, '南门1号闸机', '成人票', 1, 'FACE',   'FAIL',    '证件信息不匹配', '2025-05-29 10:05:00');

-- ---- 公告资讯 ----
INSERT INTO `announcement` (`id`, `scenic_id`, `title`, `content`, `type`, `status`, `is_top`, `publisher_id`, `publish_time`) VALUES
(1, 1, '五一假期西湖景区客流预警公告', '五一假期期间西湖景区将迎来客流高峰，请游客错峰出行，建议优先选择公共交通。', 'WARNING',  'PUBLISHED', 1, 1, '2025-04-28 09:00:00'),
(2, 1, '关于雷峰塔景点临时限流的通知', '因雷峰塔瞬时客流接近承载量，即日起实行临时限流措施。',                   'NOTICE',   'PUBLISHED', 0, 2, '2025-05-31 10:30:00'),
(3, 2, '黄山风景区索道维护公告',       '光明顶索道将于6月5日进行例行维护，当日暂停运营。',                       'NOTICE',   'PUBLISHED', 0, 4, '2025-05-30 14:00:00'),
(4, NULL, '暑期门票优惠政策上线',       '暑期学生凭学生证可享受门票半价优惠，活动时间为7月1日至8月31日。',         'ACTIVITY', 'DRAFT',     0, 1, NULL);

-- ---- 停车场 ----
INSERT INTO `parking_lot` (`scenic_id`, `lot_name`, `total_space`, `free_space`, `status`, `fee_rule`) VALUES
(1, '西湖东区停车场', 500, 120, 'BUSY', '平日5元/小时，节假日10元/小时'),
(1, '西湖西区停车场', 800, 600, 'FREE', '平日5元/小时，节假日10元/小时'),
(2, '黄山南大门停车场', 1200, 0,  'FULL', '20元/天'),
(2, '黄山换乘中心停车场', 2000, 850, 'FREE', '15元/天');

-- ---- 投诉工单 ----
INSERT INTO `complaint` (`id`, `ticket_no`, `scenic_id`, `tourist_id`, `tourist_name`, `phone`, `order_no`, `title`, `content`, `type`, `priority`, `status`, `handler_id`, `handle_time`) VALUES
(1, 'TK-20250531-001', 1, 1, '王晓明', '13812341122', 'ORD20250531001', '景区指引标识不清晰', '南门入口处指引牌较少，第一次来容易迷路，建议增加标识。', 'SUGGESTION', 'NORMAL', 'PROCESSING', 5, NULL),
(2, 'TK-20250531-002', 1, 4, '陈思思', '13612344455', NULL,             '停车场收费不合理',   '停车不到一小时却按全天收费，认为收费不合理。',           'COMPLAINT',  'URGENT', 'PENDING',    NULL, NULL),
(3, 'TK-20250530-008', 2, 2, '林小红', '13912342233', 'ORD20250531002', '索道排队时间过长',   '周末索道排队超过2小时，建议增加运力或预约机制。',       'COMPLAINT',  'NORMAL', 'CLOSED',     4, '2025-05-30 16:00:00');

-- ---- 工单回复 ----
INSERT INTO `complaint_reply` (`complaint_id`, `reply_type`, `content`, `reply_by`, `sentiment`) VALUES
(1, 'TOURIST', '南门入口处指引牌较少，第一次来容易迷路。', '王晓明', 'NEGATIVE'),
(1, 'AI',      '您好，已识别到您反映的指引问题，已转交景区运营部门处理，预计2个工作日内答复。', 'AI智能助手', NULL),
(1, 'STAFF',   '感谢您的建议，我们将在南门增加3处指引标识，预计本周内完成。', '刘芳', NULL),
(3, 'STAFF',   '您好，因周末客流较大给您带来不便深表歉意，我们已优化索道预约机制。', '赵敏', NULL);

-- ---- 客流统计（西湖今日 8:00-18:00） ----
INSERT INTO `passenger_flow` (`scenic_id`, `stat_date`, `stat_hour`, `enter_count`, `leave_count`, `current_count`, `capacity`, `warning_level`) VALUES
(1, CURDATE(), 8,  1200, 100,  1200, 80000, 'NORMAL'),
(1, CURDATE(), 9,  3500, 200,  4500, 80000, 'NORMAL'),
(1, CURDATE(), 10, 6200, 500,  10200, 80000, 'NORMAL'),
(1, CURDATE(), 11, 7800, 900,  17100, 80000, 'NORMAL'),
(1, CURDATE(), 12, 5600, 1200, 21500, 80000, 'NORMAL'),
(1, CURDATE(), 13, 4200, 1500, 24200, 80000, 'NORMAL'),
(1, CURDATE(), 14, 3800, 2100, 25900, 80000, 'NORMAL'),
(2, CURDATE(), 14, 2600, 800,  42000, 50000, 'WARNING'),
(3, CURDATE(), 14, 1800, 600,  39800, 41000, 'WARNING');

-- ---- 超载预警 ----
INSERT INTO `flow_warning` (`scenic_id`, `spot_id`, `spot_name`, `current_count`, `capacity`, `warning_level`, `status`, `handle_remark`, `handler_id`, `handle_time`) VALUES
(1, 4,      '雷峰塔',   1180, 1200, 'WARNING', 'UNHANDLED', NULL, NULL, NULL),
(2, NULL,   '景区整体', 42000, 50000, 'WARNING', 'UNHANDLED', NULL, NULL, NULL),
(3, NULL,   '景区整体', 39800, 41000, 'WARNING', 'HANDLED',   '已启动分流预案，引导游客前往次要景点', 1, '2025-05-31 11:20:00');

-- ---- AI知识库 ----
INSERT INTO `ai_knowledge` (`scenic_id`, `category`, `question`, `answer`, `keywords`, `hit_count`, `status`) VALUES
(1, 'TICKET',  '门票价格是多少？', '西湖景区大门免票，部分景点单独收费，如三潭印月55元、雷峰塔40元。', '门票,价格,多少钱', 1280, 1),
(1, 'ROUTE',   '西湖怎么玩最合理？', '推荐路线：断桥残雪→白堤→苏堤春晓→三潭印月→雷峰塔，全程约4小时。', '路线,游玩,攻略', 960,  1),
(1, 'SERVICE', '景区开放时间？', '西湖景区全天开放，收费景点开放时间为8:00-17:30。', '开放时间,几点', 720,  1),
(2, 'SAFETY',  '登山需要注意什么？', '请穿着防滑鞋，关注天气变化，雷雨天气请勿登顶，听从前山工作人员引导。', '安全,登山,注意', 430,  1),
(NULL, 'COMMON', '如何退票？', '在游玩日期前一天23:59前可在小程序订单详情页申请退票，审核通过后原路退回。', '退票,退款,取消', 610, 1);

-- ---- AI调用日志 ----
INSERT INTO `ai_log` (`scenic_id`, `module`, `input_summary`, `output_summary`, `duration`, `status`, `operator_id`) VALUES
(1, 'KNOWLEDGE',     '游客提问：西湖门票多少钱',           '返回门票价格相关知识内容',           820,  'SUCCESS', 1),
(1, 'SENTIMENT',     '工单TK-20250531-001内容情感分析',     '情感倾向：消极（NEGATIVE）',         640,  'SUCCESS', 5),
(1, 'ANNOUNCEMENT',  '生成雷峰塔限流公告草稿',             '已生成限流公告拟稿',                 1520, 'SUCCESS', 2),
(NULL, 'SUMMARY',    '批量归纳本周32条工单',                '主要问题集中在标识不清与排队时间长', 2100, 'SUCCESS', 1),
(1, 'FLOW_PLAN',     '雷峰塔瞬时客流预警，生成疏导预案',   '建议开放北侧步道并广播分流',         1180, 'SUCCESS', 2),
(2, 'KNOWLEDGE',     'AI服务调用超时',                     '大模型接口连接超时',                 15000,'FAIL',    4);

-- ---- AI会话记录 ----
INSERT INTO `ai_chat` (`session_id`, `tourist_id`, `scenic_id`, `role`, `content`) VALUES
('S20250531001', 1, 1, 'user',      '西湖门票多少钱？'),
('S20250531001', 1, 1, 'assistant', '西湖景区大门免票，三潭印月55元、雷峰塔40元，您可以在小程序直接购票哦。'),
('S20250531001', 1, 1, 'user',      '现在人多吗？'),
('S20250531001', 1, 1, 'assistant', '当前在园人数约2.6万人，属于舒适区间，建议优先游览断桥与苏堤。');

-- ---- 登录日志 ----
INSERT INTO `sys_login_log` (`user_id`, `username`, `login_type`, `ip`, `browser`, `os`, `status`, `msg`, `login_time`) VALUES
(1,    'admin',       'PC',           '192.168.1.100', 'Chrome 120',  'Windows 11', 1, '登录成功',              '2025-05-31 08:50:00'),
(2,    'xihu01',      'PC',           '192.168.1.101', 'Edge 120',    'Windows 10', 1, '登录成功',              '2025-05-31 09:01:00'),
(4,    'huangshan01', 'PC',           '192.168.1.102', 'Chrome 120',  'macOS 14',   1, '登录成功',              '2025-05-31 09:05:00'),
(NULL, 'admin',       'PC',           '10.0.0.66',     'Firefox 121', 'Ubuntu 22',  0, '密码错误，连续失败1次', '2025-05-31 07:30:00'),
(NULL, 'test999',     'MINI_PROGRAM', '117.136.0.12',  'WeChat 8.0',  'iOS 17',     0, '账号不存在',            '2025-05-30 21:14:00');

-- ---- 操作日志 ----
INSERT INTO `sys_oper_log` (`title`, `business_type`, `method`, `request_method`, `oper_url`, `oper_ip`, `oper_param`, `json_result`, `status`, `cost_time`, `operator_id`, `operator_name`, `scenic_id`, `oper_time`) VALUES
('账号管理', 'INSERT', 'com.ikun.controller.SysUserController.save',      'POST', '/system/user',  '192.168.1.100', '{"username":"xihu02","realName":"王海涛"}',     '{"code":200,"message":"新增成功"}', 1, 45,   1, 'admin',  NULL, '2025-05-31 09:10:00'),
('景区管理', 'UPDATE', 'com.ikun.controller.ScenicAreaController.update',  'PUT',  '/scenic/area',  '192.168.1.100', '{"id":1,"scenicName":"西湖风景区"}',             '{"code":200,"message":"修改成功"}', 1, 38,   1, 'admin',  NULL, '2025-05-31 09:20:00'),
('订单管理', 'EXPORT', 'com.ikun.controller.TicketOrderController.export', 'GET',  '/order/export', '192.168.1.101', '{"scenicId":1,"playDate":"2025-05-31"}',         '导出 128 条订单',                   1, 1860, 2, 'xihu01', 1,    '2025-05-31 10:00:00');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
--  初始化数据说明：
--  1. 后台账号默认密码统一为 123456（数据库存储为 MD5：e10adc3949ba59abbe56e057f20f883e）
--  2. 超级管理员：admin；景区管理员：xihu01 / huangshan01
--  3. 接口文档地址：http://localhost:8080/api/swagger-ui/index.html
-- ============================================================================
