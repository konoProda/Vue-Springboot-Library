-- ============================================================
-- Migration: Add unique constraint to prevent duplicate borrows
-- Date: 2026-07-12
-- Branch: refactor/test-drill
-- ============================================================

ALTER TABLE bookwithuser ADD UNIQUE INDEX uq_user_isbn (user_id, isbn);
