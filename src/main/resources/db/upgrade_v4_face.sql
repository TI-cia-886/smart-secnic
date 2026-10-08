-- ============================================================================
-- upgrade_v4_face.sql
-- 人脸识别模块升级脚本：
--   1. 新建 face_record 识别记录表
--   2. 权限菜单树新增「人脸识别」及按钮权限
--   3. 角色授权（超级管理员 / 景区管理员）
-- 执行方式：在 smart_scenic 库上直接执行，幂等可重复。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 人脸识别记录表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `face_record` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `scenic_id`    BIGINT                 DEFAULT NULL COMMENT '景区ID',
    `record_type`  VARCHAR(20)   NOT NULL COMMENT '记录类型：ENROLL注册 SEARCH搜索 COMPARE比对 DETECT检测',
    `tourist_id`   BIGINT                 DEFAULT NULL COMMENT '命中的游客ID（tourist.id）',
    `tourist_no`   VARCHAR(30)            DEFAULT NULL COMMENT '游客编号（即腾讯云 PersonId）',
    `tourist_name` VARCHAR(50)            DEFAULT NULL COMMENT '游客姓名',
    `confidence`   DECIMAL(5, 2)          DEFAULT NULL COMMENT '相似度/置信度 0~100',
    `face_count`   INT                    DEFAULT NULL COMMENT '检测到的人脸数',
    `detail`       VARCHAR(500)           DEFAULT NULL COMMENT '结果摘要',
    `operator_id`  BIGINT                 DEFAULT NULL COMMENT '操作人ID（sys_user.id）',
    `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_face_record_type` (`record_type`),
    KEY `idx_face_record_tourist` (`tourist_id`),
    KEY `idx_face_record_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '人脸识别记录表';

-- ----------------------------------------------------------------------------
-- 2. 权限菜单树：人脸识别（顶级菜单，ID 从 50 起避开既有 1~45）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_permission` (`id`, `parent_id`, `perm_name`, `perm_code`, `perm_type`, `path`, `icon`, `sort`, `status`) VALUES
(50, 0,  '人脸识别',   'face:list',    1, '/face',    'face',  14, 1),
(51, 50, '人脸注册',   'face:enroll',  2, NULL,       NULL,    1,  1),
(52, 50, '搜索与比对', 'face:search',  2, NULL,       NULL,    2,  1),
(53, 50, '人脸检测',   'face:detect',  2, NULL,       NULL,    3,  1);

-- ----------------------------------------------------------------------------
-- 3. 角色授权
-- ----------------------------------------------------------------------------
-- 超级管理员：全部新权限
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, `id` FROM `sys_permission` WHERE `id` IN (50, 51, 52, 53);

-- 景区管理员：人脸识别全功能（不含人脸库删除以外的管理操作已在按钮级控制）
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES
(2, 50), (2, 51), (2, 52), (2, 53);
