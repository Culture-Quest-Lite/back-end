-- =====================================================================
-- Vùng check-in theo địa điểm thật thay cho bán kính cứng 50m
--
-- Bối cảnh: check-in trước đây fix cứng 50m trong code, nên khu du lịch
-- rộng hàng trăm mét (Bà Nà Hills, Tràng An, phố cổ Hội An) không thể
-- check-in được nếu người dùng không đứng sát điểm tâm. Giờ mỗi hotspot
-- tự khai báo bán kính riêng, và có thể vẽ hẳn ranh giới polygon nếu khu
-- có hình thù bất thường mà vòng tròn mô tả không đúng.
--
-- Dự án không dùng Flyway/Liquibase, schema do Hibernate ddl-auto=update
-- quản lý. Hibernate TỰ THÊM 2 cột khi boot, nhưng KHÔNG backfill dữ liệu
-- và KHÔNG thêm constraint/index cho cột đã tồn tại. Script này làm phần
-- Hibernate không làm.
--
-- Thứ tự chạy:
--   1. Deploy code mới và để app boot 1 lần  -> Hibernate tạo 2 cột
--   2. Chạy PHẦN 1 (backfill)                -> hotspot cũ nhận 50m
--   3. Chạy PHẦN 2 (ràng buộc) + PHẦN 3      -> default/not null + index
--   4. Đọc kết quả PHẦN 4                    -> xác nhận dữ liệu đúng
--
-- LƯU Ý: sao lưu database trước khi chạy PHẦN 2.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PHẦN 0: phòng khi app chưa boot để Hibernate tự tạo cột
-- ---------------------------------------------------------------------
ALTER TABLE hotspots ADD COLUMN IF NOT EXISTS check_in_radius INTEGER;
ALTER TABLE hotspots ADD COLUMN IF NOT EXISTS boundary geometry(Polygon, 4326);


-- ---------------------------------------------------------------------
-- PHẦN 1: backfill — mọi hotspot cũ giữ nguyên hành vi 50m như trước
-- ---------------------------------------------------------------------
UPDATE hotspots
SET check_in_radius = 50
WHERE check_in_radius IS NULL;


-- ---------------------------------------------------------------------
-- PHẦN 2: ràng buộc — chỉ chạy sau khi PHẦN 1 đã xong
-- ---------------------------------------------------------------------
ALTER TABLE hotspots ALTER COLUMN check_in_radius SET DEFAULT 50;
ALTER TABLE hotspots ALTER COLUMN check_in_radius SET NOT NULL;

-- Khớp với @Min(20)/@Max(5000) ở HotspotRequest
ALTER TABLE hotspots DROP CONSTRAINT IF EXISTS chk_hotspot_check_in_radius;
ALTER TABLE hotspots ADD CONSTRAINT chk_hotspot_check_in_radius
    CHECK (check_in_radius BETWEEN 20 AND 5000);


-- ---------------------------------------------------------------------
-- PHẦN 3: index không gian cho boundary (ST_DWithin mỗi lần check-in)
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_hotspots_boundary ON hotspots USING GIST (boundary);


-- ---------------------------------------------------------------------
-- PHẦN 4: đối chiếu — chạy và ĐỌC KẾT QUẢ
-- ---------------------------------------------------------------------

-- Tổng quan
SELECT COUNT(*)                                        AS tong,
       COUNT(*) FILTER (WHERE boundary IS NOT NULL)    AS co_polygon,
       COUNT(*) FILTER (WHERE check_in_radius <> 50)   AS radius_tuy_chinh,
       COUNT(*) FILTER (WHERE check_in_radius IS NULL) AS con_thieu_radius
FROM hotspots;

-- Hotspot có polygon: tâm BẮT BUỘC nằm trong ranh giới, nếu không thì
-- /hotspots/nearby (dùng location) sẽ lệch với vùng check-in.
SELECT hotspot_id, hotspot_name
FROM hotspots
WHERE boundary IS NOT NULL
  AND NOT ST_Covers(boundary, location);
