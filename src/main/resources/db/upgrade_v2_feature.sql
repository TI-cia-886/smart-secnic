-- ============================================================================
--  智能AI景区管理系统  数据库升级脚本 V2 —— 功能补全
-- ----------------------------------------------------------------------------
--  适用：已执行过 smart_scenic.sql（+ upgrade_sensitive_data.sql）的数据库
--  内容：
--    1. 新增 3 张表：sys_login_log / sys_oper_log / ticket_stock
--    2. 新增若干业务字段：退票审核、电子票、AI 情感分析、AI 疏导预案、
--       AI 公告草稿、游客黑名单审计、知识库向量、注册来源景区等
--    3. 补充查询索引
--    4. 补全权限菜单树，并为各角色分配权限
--  特性：可重复执行（幂等），已存在则不重复创建
-- ============================================================================

USE `smart_scenic`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 工具过程：列 / 索引不存在时才创建，保证脚本可反复执行
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_column_if_absent`;
DROP PROCEDURE IF EXISTS `sp_add_index_if_absent`;

DELIMITER $$

CREATE PROCEDURE `sp_add_column_if_absent`(
    IN p_table      VARCHAR(64),
    IN p_column     VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO v_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column;
    IF v_exists = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE `sp_add_index_if_absent`(
    IN p_table   VARCHAR(64),
    IN p_index   VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO v_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND INDEX_NAME = p_index;
    IF v_exists = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD KEY `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ============================================================================
-- 一、新增表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 登录日志表（对应：系统管理 → 登录日志管理）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_login_log` (
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
-- 2. 操作日志表（对应：系统管理 → 操作日志管理，由 AOP 切面自动写入）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
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

-- ---------------------------------------------------------------------------
-- 3. 门票日期库存表（对应：购票预约 → 选择日期门票下单）
--    说明：ticket_type.stock 只是「每日库存总量」的默认值，
--          真正按游玩日期扣减库存需要本表；下单锁库存、取消回滚、超卖校验都依赖它。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ticket_stock` (
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

-- ============================================================================
-- 二、字段升级
-- ============================================================================

-- ---- sys_user：个人中心修改密码后记录变更时间 ----
CALL sp_add_column_if_absent('sys_user', 'pwd_update_time',
    "DATETIME DEFAULT NULL COMMENT '密码最近修改时间'");

-- ---- ticket_order：电子票二维码 + 待付款超时 + 退票审核全流程 ----
CALL sp_add_column_if_absent('ticket_order', 'expire_time',
    "DATETIME DEFAULT NULL COMMENT '支付截止时间，超时由定时任务自动取消'");
CALL sp_add_column_if_absent('ticket_order', 'qr_code',
    "VARCHAR(128) DEFAULT NULL COMMENT '电子票二维码内容（订单号+签名），前端据此渲染二维码'");
CALL sp_add_column_if_absent('ticket_order', 'refund_apply_time',
    "DATETIME DEFAULT NULL COMMENT '退票申请时间'");
CALL sp_add_column_if_absent('ticket_order', 'refund_audit_by',
    "BIGINT DEFAULT NULL COMMENT '退票审核人ID（sys_user.id）'");
CALL sp_add_column_if_absent('ticket_order', 'refund_audit_time',
    "DATETIME DEFAULT NULL COMMENT '退票审核时间'");
CALL sp_add_column_if_absent('ticket_order', 'refund_audit_remark',
    "VARCHAR(255) DEFAULT NULL COMMENT '退票审核意见（驳回时必填）'");

-- ---- tourist：注册来源景区（后台「游客信息管理」需按当前景区过滤）+ 黑名单审计 ----
CALL sp_add_column_if_absent('tourist', 'register_scenic_id',
    "BIGINT DEFAULT NULL COMMENT '注册来源景区ID（游客从哪个景区入口注册），用于后台按景区过滤'");
CALL sp_add_column_if_absent('tourist', 'blacklist_time',
    "DATETIME DEFAULT NULL COMMENT '加入/移出黑名单时间'");
CALL sp_add_column_if_absent('tourist', 'blacklist_operator_id',
    "BIGINT DEFAULT NULL COMMENT '黑名单操作人ID（sys_user.id）'");

-- ---- complaint：AI 情感分析结果（对应「投诉情感分析」）----
CALL sp_add_column_if_absent('complaint', 'sentiment',
    "VARCHAR(20) DEFAULT NULL COMMENT 'AI情感倾向：POSITIVE积极 NEUTRAL中性 NEGATIVE消极'");
CALL sp_add_column_if_absent('complaint', 'sentiment_score',
    "DECIMAL(4,3) DEFAULT NULL COMMENT 'AI情感得分，区间 -1.000 ~ 1.000'");
CALL sp_add_column_if_absent('complaint', 'sentiment_time',
    "DATETIME DEFAULT NULL COMMENT '情感分析时间'");

-- ---- flow_warning：AI 生成疏导预案（对应「客流预警 AI 疏导预案生成」）----
CALL sp_add_column_if_absent('flow_warning', 'ai_plan',
    "TEXT COMMENT 'AI生成的客流疏导预案全文'");
CALL sp_add_column_if_absent('flow_warning', 'ai_plan_time',
    "DATETIME DEFAULT NULL COMMENT 'AI预案生成时间'");

-- ---- announcement：AI 辅助生成公告草稿 ----
CALL sp_add_column_if_absent('announcement', 'ai_generated',
    "TINYINT NOT NULL DEFAULT 0 COMMENT '是否由AI生成：1是 0否'");
CALL sp_add_column_if_absent('announcement', 'ai_prompt',
    "VARCHAR(500) DEFAULT NULL COMMENT 'AI生成时使用的提示词，便于复现'");

-- ---- ai_knowledge：语义检索用的向量化结果（MySQL 无原生向量类型，存 JSON 数组）----
CALL sp_add_column_if_absent('ai_knowledge', 'embedding',
    "TEXT COMMENT '问题向量化结果（JSON 数组），用于语义相似度检索；不使用向量检索时留空'");
CALL sp_add_column_if_absent('ai_knowledge', 'embedding_model',
    "VARCHAR(60) DEFAULT NULL COMMENT '生成该向量的模型名，如 nomic-embed-text'");

-- ============================================================================
-- 三、索引补充
-- ============================================================================
CALL sp_add_index_if_absent('ticket_order', 'idx_scenic_status', '`scenic_id`, `status`');
CALL sp_add_index_if_absent('ticket_order', 'idx_scenic_play_date', '`scenic_id`, `play_date`');
CALL sp_add_index_if_absent('complaint', 'idx_scenic_status', '`scenic_id`, `status`');
CALL sp_add_index_if_absent('complaint', 'idx_sentiment', '`sentiment`');
CALL sp_add_index_if_absent('tourist', 'idx_register_scenic', '`register_scenic_id`');
CALL sp_add_index_if_absent('announcement', 'idx_scenic_status', '`scenic_id`, `status`');
CALL sp_add_index_if_absent('ai_log', 'idx_create_time', '`create_time`');

DROP PROCEDURE IF EXISTS `sp_add_column_if_absent`;
DROP PROCEDURE IF EXISTS `sp_add_index_if_absent`;

-- ============================================================================
-- 四、权限菜单树补全
-- ============================================================================

-- ---- 4.1 补充菜单 / 按钮权限（按需求清单的模块层级） ----
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `icon`, `sort`, `status`) VALUES
-- 基础数据（12）下的四个二级菜单
(26, 12, '景点管理',       'scenic:spot:list',           1, '/scenic/spot',         'map-pin',  1, 1),
(27, 12, '门票类型管理',   'scenic:ticket:list',         1, '/scenic/ticket',       'ticket',   2, 1),
(28, 12, '公告资讯管理',   'scenic:announcement:list',   1, '/scenic/announcement', 'notice',   3, 1),
(29, 12, '停车场点位管理', 'scenic:parking:list',        1, '/scenic/parking',      'parking',  4, 1),
-- 游客信息管理（13）
(30, 13, '注册统计图表',   'tourist:stat',               2, NULL,                   NULL,       2, 1),
-- 订单预约管理（15）
(31, 15, '订单导出',       'order:export',               2, NULL,                   NULL,       3, 1),
-- 客流统计（18）
(32, 18, '客流数据报表',   'flow:report',                2, NULL,                   NULL,       1, 1),
(33, 18, '预警处理',       'flow:warning:handle',        2, NULL,                   NULL,       2, 1),
(34, 18, '检票核验记录',   'flow:checkin:list',          2, NULL,                   NULL,       3, 1),
-- 投诉工单管理（19）
(35, 19, '情感分析',       'complaint:sentiment',        2, NULL,                   NULL,       3, 1),
(36, 19, '批量归纳汇总',   'complaint:summary',          2, NULL,                   NULL,       4, 1),
-- AI 智能管理（22）
(37, 22, '公告草稿生成',   'ai:announcement:draft',      2, NULL,                   NULL,       2, 1),
(38, 22, '疏导预案生成',   'ai:flow:plan',               2, NULL,                   NULL,       3, 1),
(39, 22, 'AI调用日志',     'ai:log:list',                2, NULL,                   NULL,       4, 1),
(40, 22, '智能客服会话',   'ai:chat:list',               2, NULL,                   NULL,       5, 1),
-- 数据统计报表（24）
(41, 24, '门票销售统计',   'report:ticket',              2, NULL,                   NULL,       2, 1),
(42, 24, '营收统计图表',   'report:revenue',             2, NULL,                   NULL,       3, 1),
-- 系统管理：日志
(43, 0,  '登录日志',       'system:loginlog:list',       1, '/system/login-log',    'login',    12, 1),
(44, 0,  '操作日志',       'system:operlog:list',        1, '/system/oper-log',     'log',      13, 1);

-- ---- 4.2 角色授权 ----
-- 超级管理员：拥有全部权限（含本次新增）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission`;

-- 景区管理员：本景区全业务，但「景区总管理」（9/10/11）仅超级管理员可见
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6), (2, 7), (2, 8),
(2, 12), (2, 26), (2, 27), (2, 28), (2, 29),
(2, 13), (2, 14), (2, 30),
(2, 15), (2, 16), (2, 17), (2, 31),
(2, 18), (2, 32), (2, 33), (2, 34),
(2, 19), (2, 20), (2, 21), (2, 35), (2, 36),
(2, 22), (2, 23), (2, 37), (2, 38), (2, 39), (2, 40),
(2, 24), (2, 25), (2, 41), (2, 42),
(2, 43), (2, 44);

-- 运营人员：票务、公告、客流
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(3, 1),
(3, 12), (3, 26), (3, 27), (3, 28), (3, 29),
(3, 15), (3, 16), (3, 17), (3, 31),
(3, 18), (3, 32), (3, 33), (3, 34),
(3, 24), (3, 25), (3, 41), (3, 42);

-- 客服人员：游客、工单
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(4, 1),
(4, 13), (4, 14), (4, 30),
(4, 15), (4, 17),
(4, 19), (4, 20), (4, 21), (4, 35), (4, 36);

-- AI训练师：知识库与 AI 能力
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(5, 1),
(5, 19), (5, 35), (5, 36),
(5, 22), (5, 23), (5, 37), (5, 38), (5, 39), (5, 40);

-- ============================================================================
-- 五、初始化数据补充
-- ============================================================================

-- ---- 登录日志示例 ----
INSERT IGNORE INTO `sys_login_log` (`id`, `user_id`, `username`, `login_type`, `ip`, `browser`, `os`, `status`, `msg`, `login_time`) VALUES
(1, 1, 'admin',       'PC',           '192.168.1.100', 'Chrome 120', 'Windows 11', 1, '登录成功',                NOW()),
(2, 2, 'xihu01',      'PC',           '192.168.1.101', 'Edge 120',   'Windows 10', 1, '登录成功',                NOW()),
(3, 4, 'huangshan01', 'PC',           '192.168.1.102', 'Chrome 120', 'macOS 14',   1, '登录成功',                NOW()),
(4, NULL, 'admin',    'PC',           '10.0.0.66',     'Firefox 121', 'Ubuntu 22', 0, '密码错误，连续失败1次',   NOW()),
(5, NULL, 'test999',  'MINI_PROGRAM', '117.136.0.12',  'WeChat 8.0', 'iOS 17',     0, '账号不存在',              NOW());

-- ---- 操作日志示例 ----
INSERT IGNORE INTO `sys_oper_log` (`id`, `title`, `business_type`, `method`, `request_method`, `oper_url`, `oper_ip`, `oper_param`, `json_result`, `status`, `cost_time`, `operator_id`, `operator_name`, `scenic_id`, `oper_time`) VALUES
(1, '账号管理', 'INSERT', 'com.ikun.controller.SysUserController.save',      'POST',   '/system/user',       '192.168.1.100', '{"username":"xihu02","realName":"王海涛"}', '{"code":200,"message":"新增成功"}', 1, 45,  1, 'admin',  NULL, NOW()),
(2, '景区管理', 'UPDATE', 'com.ikun.controller.ScenicAreaController.update',  'PUT',    '/scenic/area',       '192.168.1.100', '{"id":1,"scenicName":"西湖风景区"}',       '{"code":200,"message":"修改成功"}', 1, 38,  1, 'admin',  NULL, NOW()),
(3, '订单管理', 'EXPORT', 'com.ikun.controller.TicketOrderController.export', 'GET',    '/order/export',      '192.168.1.101', '{"scenicId":1,"playDate":"2025-05-31"}',   '导出 128 条订单',                  1, 1860, 2, 'xihu01', 1,    NOW());

-- ---- 门票日期库存示例（西湖 4 个票种 × 未来 3 天） ----
INSERT IGNORE INTO `ticket_stock` (`id`, `scenic_id`, `ticket_type_id`, `stock_date`, `total_stock`, `sold_count`, `locked_count`, `status`) VALUES
(1,  1, 1, CURDATE(),                     5000, 1240, 36, 1),
(2,  1, 1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 5000, 320,  12, 1),
(3,  1, 1, DATE_ADD(CURDATE(), INTERVAL 2 DAY), 5000, 0,    0,  1),
(4,  1, 2, CURDATE(),                     2000, 410,  8,  1),
(5,  1, 2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 2000, 96,   3,  1),
(6,  1, 3, CURDATE(),                     1000, 150,  0,  1),
(7,  1, 4, CURDATE(),                     800,  62,   4,  1),
(8,  2, 5, CURDATE(),                     4000, 2680, 75, 1),
(9,  2, 5, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 4000, 1500, 40, 1),
(10, 2, 6, CURDATE(),                     1000, 880,  22, 1);

-- ============================================================================
--  执行完成
--  说明：
--   1. 本脚本幂等，可重复执行。
--   2. 新增字段对应的 Java 实体需要同步补字段，否则 MyBatis-Plus 不会读写它们。
--   3. tourist.phone 目前仍是明文（sys_user.phone 已加密）。
--      如需统一加密，需要同时改造实体上的 MybatisEncryptTypeHandler 与查询逻辑，
--      不能只改表结构，否则查询会失配。
-- ============================================================================
