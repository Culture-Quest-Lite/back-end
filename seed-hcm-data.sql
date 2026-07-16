-- =====================================================================
-- Seed data: 4 hotspot trung tam Quan 1 + 10 story
--   Dinh Doc Lap                  : 3 story
--   Nha tho Duc Ba                : 3 story
--   Bao tang Chung tich Chien tranh: 2 story
--   Buu dien Trung tam Sai Gon    : 2 story
--
-- Tat ca PUBLISHED de AI suggest (loc status = PUBLISHED) nhin thay.
-- Toa do that, dung de test /optimize + check-in ban kinh 50m.
-- Chay: psql -h <host> -U <user> -d <db> -f seed-hcm-data.sql
-- =====================================================================

DO $$
DECLARE
    v_user_id  BIGINT := 1;   -- doi neu user tao noi dung khac

    v_tag_ls   BIGINT;  -- Lich su
    v_tag_vh   BIGINT;  -- Van hoa
    v_tag_tg   BIGINT;  -- Ton giao
    v_tag_kt   BIGINT;  -- Kien truc

    h_dinh     BIGINT;
    h_nhatho   BIGINT;
    h_baotang  BIGINT;
    h_buudien  BIGINT;
BEGIN
    -- ---------- TAG ----------
    INSERT INTO tags (tag_name, status, created_at, updated_at)
    VALUES ('Kiến trúc', 'ACTIVE', now(), now())
    ON CONFLICT (tag_name) DO NOTHING;

    SELECT tag_id INTO v_tag_ls FROM tags WHERE tag_name = 'Lịch sử';
    SELECT tag_id INTO v_tag_vh FROM tags WHERE tag_name = 'Văn hóa';
    SELECT tag_id INTO v_tag_tg FROM tags WHERE tag_name = 'Tôn giáo';
    SELECT tag_id INTO v_tag_kt FROM tags WHERE tag_name = 'Kiến trúc';

    IF v_tag_ls IS NULL OR v_tag_vh IS NULL OR v_tag_tg IS NULL THEN
        RAISE EXCEPTION 'Thieu tag Lich su / Van hoa / Ton giao trong bang tags';
    END IF;

    -- ---------- HOTSPOT ----------
    INSERT INTO hotspots (created_by, hotspot_name, address, description, history_information,
                          xp, point, location,
                          estimated_duration_min, estimated_duration_max,
                          start_time, end_time, opening_time, closing_time,
                          status, created_at, updated_at, published_at)
    VALUES (v_user_id, 'Dinh Độc Lập',
            '135 Nam Kỳ Khởi Nghĩa, Bến Thành, Quận 1, TP.HCM',
            'Công trình biểu tượng gắn với thời khắc kết thúc chiến tranh, nay là Hội trường Thống Nhất.',
            'Được xây dựng lại từ năm 1962 trên nền Dinh Norodom cũ, khánh thành năm 1966.',
            100, 100, ST_SetSRID(ST_MakePoint(106.6958, 10.7772), 4326),
            45, 90, '08:00:00', '10:00:00', '07:30:00', '16:30:00',
            'PUBLISHED', now(), now(), now())
    RETURNING hotspot_id INTO h_dinh;

    INSERT INTO hotspots (created_by, hotspot_name, address, description, history_information,
                          xp, point, location,
                          estimated_duration_min, estimated_duration_max,
                          start_time, end_time, opening_time, closing_time,
                          status, created_at, updated_at, published_at)
    VALUES (v_user_id, 'Nhà thờ Đức Bà',
            '01 Công xã Paris, Bến Nghé, Quận 1, TP.HCM',
            'Vương cung thánh đường giữa trung tâm thành phố, biểu tượng quen thuộc của Sài Gòn.',
            'Khởi công năm 1877, hoàn thành năm 1880. Vật liệu chính được chở từ Pháp sang.',
            80, 80, ST_SetSRID(ST_MakePoint(106.6990, 10.7798), 4326),
            30, 60, '08:00:00', '10:00:00', '08:00:00', '17:00:00',
            'PUBLISHED', now(), now(), now())
    RETURNING hotspot_id INTO h_nhatho;

    INSERT INTO hotspots (created_by, hotspot_name, address, description, history_information,
                          xp, point, location,
                          estimated_duration_min, estimated_duration_max,
                          start_time, end_time, opening_time, closing_time,
                          status, created_at, updated_at, published_at)
    VALUES (v_user_id, 'Bảo tàng Chứng tích Chiến tranh',
            '28 Võ Văn Tần, Phường 6, Quận 3, TP.HCM',
            'Nơi lưu giữ hiện vật và hình ảnh về hậu quả chiến tranh tại Việt Nam.',
            'Mở cửa từ năm 1975, là một trong những bảo tàng được ghé thăm nhiều nhất thành phố.',
            120, 120, ST_SetSRID(ST_MakePoint(106.6921, 10.7797), 4326),
            60, 120, '08:00:00', '10:00:00', '07:30:00', '17:30:00',
            'PUBLISHED', now(), now(), now())
    RETURNING hotspot_id INTO h_baotang;

    INSERT INTO hotspots (created_by, hotspot_name, address, description, history_information,
                          xp, point, location,
                          estimated_duration_min, estimated_duration_max,
                          start_time, end_time, opening_time, closing_time,
                          status, created_at, updated_at, published_at)
    VALUES (v_user_id, 'Bưu điện Trung tâm Sài Gòn',
            '02 Công xã Paris, Bến Nghé, Quận 1, TP.HCM',
            'Bưu điện cổ vẫn hoạt động, nổi bật với mái vòm khung thép và hai tấm bản đồ cổ.',
            'Xây dựng cuối thế kỷ 19, mang phong cách chiết trung kết hợp Á Đông.',
            80, 80, ST_SetSRID(ST_MakePoint(106.6997, 10.7799), 4326),
            30, 45, '08:00:00', '10:00:00', '07:00:00', '19:00:00',
            'PUBLISHED', now(), now(), now())
    RETURNING hotspot_id INTO h_buudien;

    -- ---------- STORY ----------
    INSERT INTO stories (tag_id, hotspot_id, route_id, created_by, order_index,
                         title, content, distance_to_next, status, created_at, updated_at)
    VALUES
    -- Dinh Doc Lap (3)
    (v_tag_ls, h_dinh, NULL, v_user_id, 1,
     'Trưa ngày 30 tháng 4 năm 1975',
     'Cánh cổng chính của Dinh bị xe tăng húc đổ vào trưa ngày 30/4/1975, đánh dấu thời khắc kết thúc chiến tranh. Chiếc xe tăng mang số hiệu 390 hiện vẫn được trưng bày trong khuôn viên, ngay trên bãi cỏ trước sảnh.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_kt, h_dinh, NULL, v_user_id, 2,
     'Bản thiết kế của kiến trúc sư Ngô Viết Thụ',
     'Dinh được thiết kế bởi kiến trúc sư Ngô Viết Thụ, người Việt đầu tiên đoạt giải Khôi nguyên La Mã. Mặt tiền với hàng lam bê tông vừa che nắng vừa gợi hình đốt trúc, là ví dụ tiêu biểu cho kiến trúc hiện đại mang bản sắc phương Đông.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_vh, h_dinh, NULL, v_user_id, 3,
     'Những căn phòng phía sau cánh cửa',
     'Bên trong Dinh là hệ thống phòng khánh tiết, phòng nội các, phòng trình quốc thư và khu hầm chỉ huy dưới lòng đất với bản đồ tác chiến cùng dàn máy điện đàm được giữ gần như nguyên trạng.',
     NULL, 'PUBLISHED', now(), now()),

    -- Nha tho Duc Ba (3)
    (v_tag_tg, h_nhatho, NULL, v_user_id, 1,
     'Vương cung thánh đường giữa lòng Sài Gòn',
     'Năm 1962 nhà thờ được Tòa Thánh nâng lên hàng Vương cung thánh đường. Tượng Đức Mẹ Hòa Bình bằng đá cẩm thạch đặt phía trước quảng trường là điểm hẹn quen thuộc của người dân thành phố.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_kt, h_nhatho, NULL, v_user_id, 2,
     'Gạch Marseille và hai tháp chuông',
     'Toàn bộ vật liệu xây dựng được chở từ Pháp sang, trong đó gạch Marseille sau gần 150 năm vẫn giữ màu đỏ nguyên bản. Hai tháp chuông cao khoảng 57 mét với bộ sáu quả chuông là điểm nhấn của công trình.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_ls, h_nhatho, NULL, v_user_id, 3,
     'Từ nhà thờ gỗ đến biểu tượng thành phố',
     'Trước khi công trình hiện tại ra đời, giáo dân Sài Gòn dùng một nhà thờ gỗ nhỏ ven kênh. Công trình mới khởi công năm 1877 và hoàn thành năm 1880, dần trở thành hình ảnh gắn liền với trung tâm thành phố.',
     NULL, 'PUBLISHED', now(), now()),

    -- Bao tang Chung tich Chien tranh (2)
    (v_tag_ls, h_baotang, NULL, v_user_id, 1,
     'Chứng tích của một thời khói lửa',
     'Bảo tàng mở cửa từ năm 1975, trưng bày hiện vật, hình ảnh và tư liệu về hậu quả chiến tranh. Khu sân ngoài trời đặt máy bay, xe tăng và pháo thu được sau chiến tranh.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_vh, h_baotang, NULL, v_user_id, 2,
     'Requiem - bộ sưu tập ảnh của những người đã ngã xuống',
     'Một trong những không gian gây xúc động nhất là bộ sưu tập ảnh do các phóng viên chiến trường từ nhiều quốc gia chụp lại, trong đó nhiều người đã hy sinh khi đang tác nghiệp.',
     NULL, 'PUBLISHED', now(), now()),

    -- Buu dien Trung tam (2)
    (v_tag_kt, h_buudien, NULL, v_user_id, 1,
     'Mái vòm thép và hai tấm bản đồ cổ',
     'Không gian chính là một mái vòm lớn đỡ bằng khung thép, hai bên tường treo hai tấm bản đồ vẽ tay từ cuối thế kỷ 19: một bản đồ Sài Gòn và vùng phụ cận, một bản đồ đường dây điện tín Nam Kỳ và Campuchia.',
     NULL, 'PUBLISHED', now(), now()),

    (v_tag_ls, h_buudien, NULL, v_user_id, 2,
     'Bưu điện vẫn còn hoạt động sau hơn một thế kỷ',
     'Khác với nhiều công trình cùng thời đã chuyển công năng, nơi đây vẫn phục vụ như một bưu điện thực thụ. Những quầy gỗ, bốt điện thoại cũ và người viết thư thuê vẫn còn ở đó.',
     NULL, 'PUBLISHED', now(), now());

    RAISE NOTICE 'Da tao hotspot: Dinh=% NhaTho=% BaoTang=% BuuDien=%', h_dinh, h_nhatho, h_baotang, h_buudien;
END $$;

-- ---------- KIEM TRA ----------
SELECT h.hotspot_id, h.hotspot_name, h.status,
       ROUND(ST_Y(h.location::geometry)::numeric, 7) AS lat,
       ROUND(ST_X(h.location::geometry)::numeric, 7) AS lon,
       COUNT(s.story_id) AS so_story
FROM hotspots h
LEFT JOIN stories s ON s.hotspot_id = h.hotspot_id
WHERE h.hotspot_name IN ('Dinh Độc Lập', 'Nhà thờ Đức Bà',
                         'Bảo tàng Chứng tích Chiến tranh', 'Bưu điện Trung tâm Sài Gòn')
  AND h.status = 'PUBLISHED'
GROUP BY h.hotspot_id, h.hotspot_name, h.status, h.location
ORDER BY h.hotspot_id;

-- ---------- TUY CHON: don du lieu rac cu ----------
-- 6 hotspot DRAFT toa do Can Tho + ban trung "Dinh Doc Lap"/"Cho Ben Thanh" cu.
-- Bo comment neu muon an chung khoi API (khong xoa cung):
-- UPDATE hotspots SET status = 'DELETED' WHERE hotspot_id IN (4, 8, 9, 10, 11, 12, 19, 20, 21);
