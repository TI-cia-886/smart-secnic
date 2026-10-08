-- ============================================================================
--  敏感数据加密改造 · 升级脚本
--  适用场景：数据库已存在且有数据，不想重建
--  新建数据库无需执行本文件，直接执行 smart_scenic.sql 即可
-- ============================================================================

USE `smart_scenic`;

-- 1. 密码列放宽长度：BCrypt 哈希为 60 位，原 MD5 为 32 位
ALTER TABLE `sys_user`
    MODIFY COLUMN `password` VARCHAR(100) NOT NULL COMMENT '登录密码（BCrypt 单向哈希，不可逆）';

-- 2. 手机号列放宽长度：AES-256-GCM 密文 Base64 后约为 44+ 字符
ALTER TABLE `sys_user`
    MODIFY COLUMN `phone` VARCHAR(255) DEFAULT NULL COMMENT '手机号（AES-256-GCM 加密存储，不落明文）';

-- 3. 新增手机号 SHA-256 摘要列，用于加密字段的精确检索与唯一性校验
ALTER TABLE `sys_user`
    ADD COLUMN `phone_hash` CHAR(64) DEFAULT NULL COMMENT '手机号 SHA-256 加盐摘要（不可逆），用于精确检索'
        AFTER `phone`;

-- 4. 手机号唯一约束（MySQL 唯一索引允许多个 NULL，不影响未填手机号的账号）
ALTER TABLE `sys_user`
    ADD UNIQUE KEY `uk_phone_hash` (`phone_hash`);

-- ----------------------------------------------------------------------------
-- 执行完成后启动应用，DataEncryptionMigrationRunner 会自动完成：
--   a) 把仍是明文的手机号加密为 AES-256-GCM 密文；
--   b) 回填 phone_hash 摘要。
-- 历史 MD5 密码会在各账号下次登录成功时自动升级为 BCrypt，无需手工处理。
-- ----------------------------------------------------------------------------
