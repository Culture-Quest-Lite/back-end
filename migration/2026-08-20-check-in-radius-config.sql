-- =====================================================================
-- Bán kính check-in do ADMIN cấu hình (thay cho khoảng 20m - 5000m hardcode)
--
-- Hibernate (ddl-auto: update) tự tạo bảng check_in_radius_config khi khởi động
-- với code mới, nhưng KHÔNG chèn dòng cấu hình. Backend cũng tự tạo dòng mặc định
-- ở lần đọc đầu tiên; script này để seed sẵn / chỉnh nhanh bằng SQL nếu cần.
--
-- Chạy SAU khi đã khởi động backend ít nhất một lần với code mới.
-- =====================================================================

INSERT INTO check_in_radius_config (min_radius, max_radius, default_radius, updated_at, updated_by)
SELECT 20, 5000, 50, NOW(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM check_in_radius_config);

-- Ví dụ đổi khoảng cho phép (admin thường làm qua API PUT /api/v1/configs/check-in-radius):
-- UPDATE check_in_radius_config SET min_radius = 10, max_radius = 20000, default_radius = 100;

-- Hotspot cũ chưa có bán kính -> lấy mặc định đang cấu hình
UPDATE hotspots h
SET check_in_radius = (SELECT c.default_radius FROM check_in_radius_config c ORDER BY c.config_id LIMIT 1)
WHERE h.check_in_radius IS NULL;
