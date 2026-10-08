-- ============================================================================
--  智能AI景区管理系统  数据库升级脚本 V3 —— 游客小程序账号体系
-- ----------------------------------------------------------------------------
--  适用：已执行过 smart_scenic.sql（+ upgrade_sensitive_data.sql
--        + upgrade_v2_feature.sql）的数据库
--  内容：
--    1. tourist 表新增 phone_hash（手机号 SHA-256 加盐摘要）与 password（BCrypt）
--    2. phone_hash 建立唯一索引，从数据库层面兜住「同一手机号重复注册」
--  特性：可重复执行（幂等），已存在则不重复创建
--
--  说明：
--    · phone_hash 由应用侧使用配置项 security.phone-hash-salt 加盐计算，
--      SQL 无法还原该盐值，因此历史行保持 NULL。
--    · 游客首次登录时，应用会自动补写 phone_hash，并在账号无密码时
--      以「手机号后六位」作为初始密码写入，老账号无需人工干预即可登录。
--    · MySQL 唯一索引允许多个 NULL，因此存量数据不会因该索引而冲突。
-- ============================================================================

USE `smart_scenic`;
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 工具过程：列 / 唯一索引不存在时才创建，保证脚本可反复执行
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_column_if_absent`;
DROP PROCEDURE IF EXISTS `sp_add_unique_index_if_absent`;

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

CREATE PROCEDURE `sp_add_unique_index_if_absent`(
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
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE KEY `', p_index, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- ============================================================================
-- 一、tourist 表新增小程序登录字段
-- ============================================================================
CALL sp_add_column_if_absent('tourist', 'phone_hash',
    'CHAR(64) DEFAULT NULL COMMENT ''手机号SHA-256加盐摘要，用于精确检索与唯一性校验''');

CALL sp_add_column_if_absent('tourist', 'password',
    'VARCHAR(100) DEFAULT NULL COMMENT ''登录密码（BCrypt单向哈希，不可逆）''');

-- ============================================================================
-- 二、手机号摘要唯一索引
-- ============================================================================
CALL sp_add_unique_index_if_absent('tourist', 'uk_tourist_phone_hash', '`phone_hash`');

-- ============================================================================
-- 三、补充「删除景区」权限
-- ----------------------------------------------------------------------------
-- 景区管理的增删改查此前只有 list/add/edit 三个权限码，删除接口没有对应权限，
-- 这里补齐按钮权限；仅授予超级管理员（景区总管理 9/10/11 本就不下放给其他角色）。
-- ============================================================================
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `icon`, `sort`, `status`) VALUES
(45, 9, '删除景区', 'scenic:area:delete', 2, NULL, NULL, 3, 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission` WHERE `id` = 45;

-- ---------------------------------------------------------------------------
-- 清理工具过程
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `sp_add_column_if_absent`;
DROP PROCEDURE IF EXISTS `sp_add_unique_index_if_absent`;
