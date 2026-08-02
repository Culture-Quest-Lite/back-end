-- =====================================================================
-- Chuyển ảnh đơn của voucher và route từ bảng medias sang cột image_url
--
-- Bối cảnh: dự án không dùng Flyway/Liquibase, schema do Hibernate
-- ddl-auto=update quản lý. Hibernate TỰ THÊM cột image_url khi boot,
-- nhưng KHÔNG backfill dữ liệu và KHÔNG drop cột cũ. Script này làm
-- phần Hibernate không làm.
--
-- Thứ tự chạy:
--   1. Deploy code mới và để app boot 1 lần  -> Hibernate tạo 3 cột image_url
--   2. Chạy PHẦN 1 (backfill)                -> copy dữ liệu sang cột mới
--   3. Kiểm tra PHẦN 2 (đối chiếu)           -> xác nhận không mất dữ liệu
--   4. Chạy PHẦN 3 (dọn dẹp)                 -> chỉ khi bước 3 đã ổn
--
-- LƯU Ý: sao lưu database trước khi chạy PHẦN 3.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PHẦN 0: phòng khi app chưa boot để Hibernate tự tạo cột
-- ---------------------------------------------------------------------
ALTER TABLE tags     ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE vouchers ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE routes   ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);


-- ---------------------------------------------------------------------
-- PHẦN 1: backfill — lấy ảnh có display_order nhỏ nhất của mỗi entity
-- ---------------------------------------------------------------------

-- Voucher
UPDATE vouchers v
SET image_url = m.file_url
FROM (
    SELECT DISTINCT ON (voucher_id) voucher_id, file_url
    FROM medias
    WHERE voucher_id IS NOT NULL
      AND media_type = 'IMAGE'
    ORDER BY voucher_id, display_order ASC NULLS LAST, media_id ASC
) m
WHERE v.voucher_id = m.voucher_id
  AND v.image_url IS NULL;

-- Route
UPDATE routes r
SET image_url = m.file_url
FROM (
    SELECT DISTINCT ON (route_id) route_id, file_url
    FROM medias
    WHERE route_id IS NOT NULL
      AND media_type = 'IMAGE'
    ORDER BY route_id, display_order ASC NULLS LAST, media_id ASC
) m
WHERE r.route_id = m.route_id
  AND r.image_url IS NULL;


-- ---------------------------------------------------------------------
-- PHẦN 2: đối chiếu — chạy và ĐỌC KẾT QUẢ trước khi sang phần 3
-- ---------------------------------------------------------------------

-- 2a. Số entity đã có ảnh sau backfill (nên khớp với số entity từng có media IMAGE)
SELECT 'vouchers' AS entity,
       (SELECT COUNT(*) FROM vouchers WHERE image_url IS NOT NULL)                        AS da_backfill,
       (SELECT COUNT(DISTINCT voucher_id) FROM medias
         WHERE voucher_id IS NOT NULL AND media_type = 'IMAGE')                           AS ky_vong
UNION ALL
SELECT 'routes',
       (SELECT COUNT(*) FROM routes WHERE image_url IS NOT NULL),
       (SELECT COUNT(DISTINCT route_id) FROM medias
         WHERE route_id IS NOT NULL AND media_type = 'IMAGE');

-- 2b. CẢNH BÁO: các file sẽ MẤT khi xóa row medias.
--     Gồm ảnh thứ 2 trở đi, và mọi file không phải IMAGE (video/audio/other).
--     Nếu có dòng trả về, cân nhắc tải các file này về hoặc giữ lại trước khi xóa.
SELECT media_id, voucher_id, route_id, media_type, display_order, file_url
FROM medias
WHERE (voucher_id IS NOT NULL OR route_id IS NOT NULL)
  AND file_url NOT IN (
      SELECT image_url FROM vouchers WHERE image_url IS NOT NULL
      UNION
      SELECT image_url FROM routes   WHERE image_url IS NOT NULL
  )
ORDER BY voucher_id NULLS LAST, route_id NULLS LAST, display_order;


-- ---------------------------------------------------------------------
-- PHẦN 3: dọn dẹp — CHỈ chạy sau khi phần 2 đã được kiểm tra
--
-- Các row bị xóa ở đây chỉ mất bản ghi DB; file trên S3 vẫn còn.
-- Nếu muốn thu hồi dung lượng S3, xuất danh sách file_url ở query 2b
-- trước khi xóa rồi dọn bằng AWS CLI.
-- ---------------------------------------------------------------------

-- 3a. Xóa các bản ghi media của voucher và route
DELETE FROM medias WHERE voucher_id IS NOT NULL OR route_id IS NOT NULL;

-- 3b. Bỏ 2 cột FK khỏi bảng medias (Hibernate không tự drop)
ALTER TABLE medias DROP COLUMN IF EXISTS voucher_id;
ALTER TABLE medias DROP COLUMN IF EXISTS route_id;
