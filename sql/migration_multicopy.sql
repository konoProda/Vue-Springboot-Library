-- ============================================================
-- Migration: 单副本 → 多副本 (Multi-Copy Support)
-- Date: 2026-07-10
-- Branch: refactor/test-drill
--
-- Changes:
--   1. Add total_copies and available_copies columns
--   2. Initialize: status='1' → available=1, total=1
--                  status='0' → available=0, total=1
--   3. Drop old status column
-- ============================================================

-- Step 1: Add new columns (nullable first, will be filled then set NOT NULL)
ALTER TABLE book
    ADD COLUMN total_copies    INT NOT NULL DEFAULT 1 COMMENT '总馆藏数',
    ADD COLUMN available_copies INT NOT NULL DEFAULT 1 COMMENT '当前可借数量';

-- Step 2: Initialize available_copies based on old status
--   status = '1' (可借) → available_copies = 1
--   status = '0' (已借出) → available_copies = 0
UPDATE book SET available_copies = 0 WHERE status = '0';

-- Step 3: Drop the old status column
ALTER TABLE book DROP COLUMN status;
