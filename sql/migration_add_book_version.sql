-- ============================================================
-- 为 book 表增加乐观锁版本号字段
-- 运行方式: mysql -u root -p springboot_vue_test < sql/migration_add_book_version.sql
-- ============================================================

-- 1. 添加 version 列，默认值为 0
ALTER TABLE book
    ADD COLUMN version INT DEFAULT 0 NOT NULL
    COMMENT '乐观锁版本号';

-- 2. 确保已有数据的 version 为 0
UPDATE book SET version = 0 WHERE version IS NULL;
