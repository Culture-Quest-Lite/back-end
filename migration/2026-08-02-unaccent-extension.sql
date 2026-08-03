-- =====================================================================
-- Bật extension unaccent cho tìm kiếm không dấu (GET /api/tags?search=)
--
-- PHẢI CHẠY TRƯỚC KHI DEPLOY code mới. Nếu chưa bật, mọi request search
-- tag sẽ lỗi SQL: function unaccent(text) does not exist.
--
-- Cần quyền superuser. Extension nằm trong postgresql-contrib, đã có sẵn
-- trong image postgis/postgis:16-3.4-alpine mà dự án đang dùng.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS unaccent;

-- Kiểm tra: cả 3 dòng đều phải trả về 'am thuc' / 'da nang' / 'dinh lang'
SELECT unaccent(lower('Ẩm thực'))  AS am_thuc,
       unaccent(lower('Đà Nẵng'))  AS da_nang,
       unaccent(lower('Đình làng')) AS dinh_lang;
