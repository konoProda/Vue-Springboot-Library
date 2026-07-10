-- ============================================================
-- Migration: Fix bookwithuser primary key design
-- Date: 2026-07-10
-- Branch: refactor/test-drill
--
-- Changes:
--   1. Drop PRIMARY KEY on book_name (incorrect design)
--   2. Rename id column → user_id (it stores reader IDs, not PK)
--   3. Add new id BIGINT AUTO_INCREMENT as proper PRIMARY KEY
-- ============================================================

-- Step 1: Drop the incorrect primary key on book_name
ALTER TABLE bookwithuser DROP PRIMARY KEY;

-- Step 2: Rename existing id column (stores user/reader ID) to user_id
ALTER TABLE bookwithuser CHANGE COLUMN id user_id BIGINT NOT NULL COMMENT '读者id';

-- Step 3: Add new auto-increment primary key column
ALTER TABLE bookwithuser ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- Step 4: Remove old index on the renamed column (if exists)
-- ALTER TABLE bookwithuser DROP INDEX id;
ALTER TABLE bookwithuser ADD INDEX idx_user_id (user_id);
