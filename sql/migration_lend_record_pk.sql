-- ============================================================
-- Migration: Add auto-increment primary key to lend_record
-- Date: 2026-07-12
-- Branch: refactor/test-drill
-- ============================================================

ALTER TABLE lend_record ADD COLUMN id BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;
