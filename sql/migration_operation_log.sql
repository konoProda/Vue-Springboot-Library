-- ============================================================
-- Migration: Add operation_log table for audit trail
-- Date: 2026-07-11
-- Branch: refactor/test-drill
-- ============================================================

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '操作用户ID',
    `username`       VARCHAR(255) NOT NULL COMMENT '操作用户名(冗余)',
    `operation_type` VARCHAR(50)  NOT NULL COMMENT '操作类型: BORROW/RETURN/RENEW/DELETE_BOOK/DELETE_USER等',
    `detail`         TEXT         DEFAULT NULL COMMENT '操作详情(JSON格式)',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_operation_type` (`operation_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
