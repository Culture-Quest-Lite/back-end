-- =====================================================================
-- Culture Guard: kiểm duyệt chủ đề văn hóa - di sản - lịch sử cho tag/story
--
-- Hibernate (ddl-auto: update) tự thêm các cột culture_*, moderate_*, reject_reason
-- khi khởi động, nhưng KHÔNG backfill dữ liệu và KHÔNG tạo index.
-- Script này làm phần Hibernate không làm.
--
-- Chạy SAU khi đã khởi động backend ít nhất một lần với code mới.
-- =====================================================================

-- ---------------------------------------------------------------------
-- BẮT BUỘC chạy trước: nới CHECK constraint của cột enum status.
-- Hibernate sinh các constraint này lúc tạo bảng và KHÔNG bao giờ sửa lại,
-- nên giá trị enum mới (PENDING_REVIEW, REJECTED) sẽ bị chặn:
--   ERROR: new row for relation "tags" violates check constraint "tags_status_check"
-- ---------------------------------------------------------------------

ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_status_check;
ALTER TABLE tags ADD CONSTRAINT tags_status_check
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING_REVIEW', 'REJECTED', 'DELETED'));

ALTER TABLE stories DROP CONSTRAINT IF EXISTS stories_status_check;
ALTER TABLE stories ADD CONSTRAINT stories_status_check
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'PUBLISHED', 'DELETED'));

-- hotspots dùng chung enum ContentStatus với stories nên phải đồng bộ theo
ALTER TABLE hotspots DROP CONSTRAINT IF EXISTS hotspots_status_check;
ALTER TABLE hotspots ADD CONSTRAINT hotspots_status_check
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'PUBLISHED', 'DELETED'));

ALTER TABLE tags ADD COLUMN IF NOT EXISTS culture_score DOUBLE PRECISION;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS culture_reason TEXT;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS culture_checked_at TIMESTAMP;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS moderate_by BIGINT;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS moderate_at TIMESTAMP;
ALTER TABLE tags ADD COLUMN IF NOT EXISTS reject_reason TEXT;

ALTER TABLE stories ADD COLUMN IF NOT EXISTS culture_score DOUBLE PRECISION;
ALTER TABLE stories ADD COLUMN IF NOT EXISTS culture_reason TEXT;
ALTER TABLE stories ADD COLUMN IF NOT EXISTS culture_checked_at TIMESTAMP;
ALTER TABLE stories ADD COLUMN IF NOT EXISTS moderate_by BIGINT;
ALTER TABLE stories ADD COLUMN IF NOT EXISTS moderate_at TIMESTAMP;
ALTER TABLE stories ADD COLUMN IF NOT EXISTS reject_reason TEXT;

-- Bản trước dùng INACTIVE làm trạng thái từ chối văn hóa, giờ tách thành REJECTED.
-- Chỉ đổi những tag có reject_reason (do bộ lọc văn hóa đặt), không đụng
-- tag bị admin chủ động vô hiệu hóa.
UPDATE tags
SET status = 'REJECTED'
WHERE status = 'INACTIVE' AND reject_reason IS NOT NULL;

-- Nội dung tạo trước khi có bộ lọc coi như đã được duyệt thủ công,
-- đánh dấu điểm 1.0 để không bị lẫn vào hàng chờ và để thống kê không bị null.
UPDATE tags
SET culture_score = 1.0,
    culture_reason = 'Nội dung tạo trước khi bật bộ lọc văn hóa'
WHERE culture_score IS NULL;

UPDATE stories
SET culture_score = 1.0,
    culture_reason = 'Nội dung tạo trước khi bật bộ lọc văn hóa'
WHERE culture_score IS NULL;

-- Hàng chờ duyệt lọc theo status nên cần index
CREATE INDEX IF NOT EXISTS idx_tags_status ON tags (status);
CREATE INDEX IF NOT EXISTS idx_stories_status ON stories (status);

-- ---------------------------------------------------------------------
-- Kiểm chứng
-- ---------------------------------------------------------------------
SELECT status, COUNT(*) AS total, COUNT(culture_score) AS scored
FROM tags
GROUP BY status
ORDER BY status;

SELECT status, COUNT(*) AS total, COUNT(culture_score) AS scored
FROM stories
GROUP BY status
ORDER BY status;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'c' AND conrelid::regclass::text IN ('tags', 'stories', 'hotspots')
ORDER BY table_name;
