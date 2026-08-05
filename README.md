# Check-in theo vùng địa lý thật (giống Google Maps) thay cho bán kính cứng 50m

## Context

Hiện tại toàn bộ logic check-in nằm ở đúng **một dòng magic number** tại
[UserHotspotProgressServiceImpl.java:55](src/main/java/org/sep490/backend/module/exploration/service/impl/UserHotspotProgressServiceImpl.java#L55):

```java
if (distance > 50.0) {
    throw new BusinessException("Bạn đang ở ngoài vùng check-in. Hãy di chuyển vào bán kính 50m để check-in");
}
```

Vấn đề: một khu du lịch rộng hàng trăm mét (Bà Nà Hills, Tràng An, phố cổ Hội An) chỉ có **một điểm tâm** trong DB. Người dùng đứng giữa khu du lịch, cách điểm tâm 300m, vẫn bị từ chối check-in dù họ thực sự đã đến nơi.

Google Maps không dùng bán kính cố định. Mỗi POI có **viewport/ranh giới** riêng — địa điểm càng lớn thì vùng "đã đến" càng rộng — và họ cộng thêm **sai số GPS** của thiết bị để tránh báo sai khi tín hiệu yếu (trong nhà, rừng núi, hẻm sâu).

Kết quả mong muốn: check-in được chấp nhận khi người dùng thực sự nằm trong địa điểm, bất kể địa điểm rộng bao nhiêu — với 3 lớp quyết định theo thứ tự ưu tiên:

1. **Polygon ranh giới** (chính xác nhất) — curator vẽ ranh giới thật trên bản đồ.
2. **Bán kính riêng mỗi hotspot** — fallback khi chưa vẽ polygon; curator chỉ cần nhập một con số.
3. **Sai số GPS** — nới ngưỡng theo `accuracy` mà thiết bị báo về, có giới hạn trần chống gian lận.

### Ràng buộc kỹ thuật đã xác minh

| Điều kiện | Trạng thái |
|---|---|
| PostGIS | Có — `postgis/postgis:16-3.4-alpine` trong [docker-compose.yml](docker-compose.yml) |
| `hibernate-spatial` | Có — [pom.xml:136-140](pom.xml#L136-L140) |
| Pattern native query PostGIS | Có sẵn — `ST_DWithin`, `ST_Within` trong [HotspotRepository.java:17-40](src/main/java/org/sep490/backend/module/content/repository/HotspotRepository.java#L17-L40) |
| `jts-io-common` (GeoJsonReader) | **KHÔNG có** trong local m2 — chỉ có `jts-core` |
| Flyway/Liquibase | Không dùng. Schema do `ddl-auto: update` ([application.yml:18-19](src/main/resources/application.yml#L18-L19)) |
| Endpoint hotspot | `multipart/form-data` + `@ModelAttribute` → polygon phải là **field chuỗi** |

> **Hệ quả then chốt:** vì không có `jts-io-common`, ta **không** parse GeoJSON trong Java. Thay vào đó dùng PostGIS `ST_GeomFromGeoJSON` — vừa tránh thêm dependency, vừa dùng lại đúng pattern native query đã có trong repo.

---

## Thiết kế: 3 lớp quyết định

```
checkIn(hotspotId, lat, lng, accuracy)
        │
        ├─ hotspot.boundary != null ?
        │     └─ CÓ  → ST_DWithin(boundary::geography, user::geography, tolerance)
        │              (nằm TRONG polygon, hoặc cách mép ≤ tolerance)
        │
        └─ KHÔNG → distance = haversine(user, hotspot.location)
                   allowed  = COALESCE(hotspot.radiusMeters, 50) + tolerance
                   distance <= allowed ?

tolerance = min(accuracy ?: 0, 100)   // trần 100m chống giả mạo accuracy
```

Vì sao `ST_DWithin` chứ không phải `ST_Within`: `ST_DWithin(polygon, point, 0)` đã tương đương "nằm trong polygon", nhưng khi `tolerance > 0` nó tự động cho phép người dùng đứng **sát mép ngoài** trong phạm vi sai số GPS. Một hàm phục vụ cả hai nhu cầu.

Cast sang `geography` để PostGIS tính bằng **mét thật trên ellipsoid** — nhất quán với `findNearbyHotspotsWithStatus` đã có.

---

## Các thay đổi

### 1. Entity: thêm 2 cột vào `Hotspot`

[src/main/java/org/sep490/backend/module/content/entity/Hotspot.java](src/main/java/org/sep490/backend/module/content/entity/Hotspot.java) — thêm ngay dưới field `location` (dòng 55-56):

```java
@Column(name = "radius_meters")
Integer radiusMeters;

@Column(name = "boundary", columnDefinition = "geometry(Polygon, 4326)")
Polygon boundary;
```

- `radiusMeters` để **nullable** trong entity dù DB có `NOT NULL DEFAULT 50` — code luôn null-safe, tránh vỡ nếu migration chưa chạy.
- `boundary` khai báo `columnDefinition` tường minh, theo đúng cách [PartnerInfo.java:44-45](src/main/java/org/sep490/backend/module/admin/entity/PartnerInfo.java#L44-L45) đã làm (`Hotspot.location` hiện thiếu điều này — không sửa trong phạm vi task này).
- Import `org.locationtech.jts.geom.Polygon`.

### 2. Hằng số check-in

Tạo `src/main/java/org/sep490/backend/module/exploration/service/impl/CheckInPolicy.java` — repo **không có** package constants nào, nên đặt cạnh service dùng nó:

```java
public final class CheckInPolicy {
    public static final int DEFAULT_RADIUS_METERS = 50;
    public static final int MIN_RADIUS_METERS     = 20;
    public static final int MAX_RADIUS_METERS     = 5000;
    public static final double MAX_GPS_ACCURACY_TOLERANCE_METERS = 100.0;

    public static double toleranceFrom(Double accuracy) {
        if (accuracy == null || accuracy <= 0) return 0.0;
        return Math.min(accuracy, MAX_GPS_ACCURACY_TOLERANCE_METERS);
    }

    public static int effectiveRadius(Integer radiusMeters) {
        return radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS;
    }

    private CheckInPolicy() {}
}
```

### 3. Repository: 2 native query PostGIS

[src/main/java/org/sep490/backend/module/content/repository/HotspotRepository.java](src/main/java/org/sep490/backend/module/content/repository/HotspotRepository.java) — thêm theo đúng style native query đã có:

```java
// Kiểm tra user có nằm trong polygon ranh giới (hoặc sát mép trong phạm vi sai số GPS)
@Query(value = "SELECT ST_DWithin(" +
        "  CAST(h.boundary AS geography), " +
        "  CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography), " +
        "  :toleranceMeters) " +
        "FROM hotspots h WHERE h.hotspot_id = :hotspotId AND h.boundary IS NOT NULL",
        nativeQuery = true)
Boolean isWithinBoundary(@Param("hotspotId") Long hotspotId,
                         @Param("lon") double lon, @Param("lat") double lat,
                         @Param("toleranceMeters") double toleranceMeters);

// Khoảng cách tới mép polygon, dùng cho thông báo lỗi động
@Query(value = "SELECT ST_Distance(" +
        "  CAST(h.boundary AS geography), " +
        "  CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography)) " +
        "FROM hotspots h WHERE h.hotspot_id = :hotspotId AND h.boundary IS NOT NULL",
        nativeQuery = true)
Double distanceToBoundary(@Param("hotspotId") Long hotspotId,
                          @Param("lon") double lon, @Param("lat") double lat);
```

Cả hai trả về wrapper type và **có thể null** (khi hotspot không có boundary) — service phải xử lý null, không được unbox thẳng.

### 4. Parse + validate GeoJSON qua PostGIS

Thêm vào `HotspotRepository` (dùng khi curator tạo/sửa hotspot):

```java
// Parse GeoJSON -> WKT đã chuẩn hoá; ném exception nếu GeoJSON sai định dạng
@Query(value = "SELECT ST_AsText(ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geoJson AS text)), 4326))",
        nativeQuery = true)
String parseGeoJsonToWkt(@Param("geoJson") String geoJson);
```

Service bắt `DataAccessException` từ query này và đổi thành `BusinessException("Ranh giới không đúng định dạng GeoJSON")` — PostGIS ném lỗi SQL thô khi GeoJSON hỏng, không được để lọt ra client.

Sau khi có WKT, dùng `org.locationtech.jts.io.WKTReader` (**có sẵn trong `jts-core`**) để chuyển thành `Polygon` gán vào entity. Đây là lý do đi đường vòng qua WKT: PostGIS làm việc parse GeoJSON, `jts-core` làm việc dựng object — không cần thêm dependency nào.

### 5. Service check-in

[src/main/java/org/sep490/backend/module/exploration/service/impl/UserHotspotProgressServiceImpl.java](src/main/java/org/sep490/backend/module/exploration/service/impl/UserHotspotProgressServiceImpl.java) — thay khối dòng 50-57. Tách phần đánh giá ra một method dùng chung với API preview:

```java
double tolerance = CheckInPolicy.toleranceFrom(request.getAccuracy());
CheckInEvaluation eval = evaluate(hotspot, request.getLatitude(), request.getLongitude(), tolerance);

if (!eval.eligible()) {
    throw new BusinessException(String.format(
        "Bạn đang cách %.0fm, cần vào trong phạm vi %.0fm để check-in",
        eval.distanceMeters(), eval.requiredMeters()));
}
```

`evaluate(...)` là method private trả về record `CheckInEvaluation(boolean eligible, double distanceMeters, double requiredMeters)`:

- **Có boundary**: `eligible = isWithinBoundary(...)`; `distanceMeters = distanceToBoundary(...)`; `requiredMeters = tolerance`. Nếu query trả `null` (race: boundary bị xoá giữa chừng) thì rơi xuống nhánh bán kính.
- **Không boundary**: `distanceMeters = SpatialUtils.calculateDistanceInMeters(...)`; `requiredMeters = effectiveRadius(hotspot.getRadiusMeters()) + tolerance`; `eligible = distance <= required`.

Cần inject thêm `HotspotRepository` vào service này (hiện chỉ có `HotspotService`).

**Lưu ý fail-open đã phát hiện:** [SpatialUtils.java:18](src/main/java/org/sep490/backend/common/utils/SpatialUtils.java#L18) trả `0.0` khi Point null → hotspot thiếu `location` sẽ **luôn pass** check-in. Trong nhánh không-boundary, thêm guard rõ ràng: nếu `hotspot.getLocation() == null` thì ném `BusinessException("Hotspot chưa có toạ độ hợp lệ")`. Không sửa `SpatialUtils` vì có 3 nơi khác đang dùng.

### 6. Request DTO: thêm `accuracy`

[UserHotspotProgressRequest.java](src/main/java/org/sep490/backend/module/exploration/dto/request/UserHotspotProgressRequest.java) — thêm field **optional** (không `@NotNull`, giữ tương thích ngược với app cũ):

```java
@PositiveOrZero(message = "Độ chính xác GPS không hợp lệ")
@Schema(description = "Sai số GPS (mét) do thiết bị báo về, lấy từ coords.accuracy", example = "12.5")
Double accuracy;
```

Phía mobile Expo: `Location.getCurrentPositionAsync()` trả `coords.accuracy` sẵn — chỉ cần gửi kèm.

### 7. Hotspot request/response + mapper

[HotspotRequest.java](src/main/java/org/sep490/backend/module/content/dto/request/HotspotRequest.java) — thêm 2 field:

```java
@Min(value = 20,   message = "Bán kính check-in tối thiểu 20m")
@Max(value = 5000, message = "Bán kính check-in tối đa 5000m")
Integer radiusMeters;          // null -> service set 50

@Schema(description = "Ranh giới GeoJSON dạng Polygon; để trống nếu dùng bán kính",
        example = "{\"type\":\"Polygon\",\"coordinates\":[[[105.851,21.028],[105.855,21.028],[105.855,21.031],[105.851,21.031],[105.851,21.028]]]}")
String boundaryGeoJson;
```

[HotspotResponse.java](src/main/java/org/sep490/backend/module/content/dto/response/HotspotResponse.java) — thêm `Integer radiusMeters;` và `String boundaryGeoJson;` để front-end vẽ lại ranh giới khi mở form sửa.

[HotspotMapper.java](src/main/java/org/sep490/backend/module/content/mapper/HotspotMapper.java) — **bắt buộc** thêm `@Mapping` tường minh cho cả 3 method:
- `toEntity` / `updateFromRequest`: `@Mapping(target = "boundary", ignore = true)` — service tự set sau khi parse qua PostGIS, mapper không tự chuyển String→Polygon được.
- `toResponse`: `@Mapping(target = "boundaryGeoJson", ignore = true)` — service tự đổ vào.

> Mapper đang để `unmappedTargetPolicy = ReportingPolicy.IGNORE` ([dòng 22](src/main/java/org/sep490/backend/module/content/mapper/HotspotMapper.java#L22)) nên **thiếu mapping sẽ im lặng thành null, không lỗi build**. Đây là bẫy đã ghi nhận — phải khai báo tường minh.

Để trả `boundaryGeoJson` về client, thêm query `SELECT ST_AsGeoJSON(h.boundary) FROM hotspots h WHERE h.hotspot_id = :id`, gọi trong `buildHotspotResponse` **chỉ ở `getDetail`** — không gọi trong `getAll`/`filterHotspots` để tránh N+1.

### 8. Validate ở `create()` và `update()`

[HotspotServiceImpl.java](src/main/java/org/sep490/backend/module/content/service/impl/HotspotServiceImpl.java) — tách các kiểm tra hiện có ở `create` (dòng 56-66) thành method `validateHotspotRequest(request)` dùng chung, rồi **gọi cả trong `update`** (dòng 90-108 hiện **không validate gì cả** — có thể sửa toạ độ ra ngoài Việt Nam).

Method này gồm: kiểm tra Việt Nam (`isLocationInVietnam`), thứ tự thời gian, min/max duration — cộng thêm phần polygon:

```java
if (StringUtils.hasText(request.getBoundaryGeoJson())) {
    String wkt = parse qua ST_GeomFromGeoJSON (bắt DataAccessException -> BusinessException)
    Geometry g = new WKTReader().read(wkt);
    - phải là Polygon  -> nếu không: "Ranh giới phải là một vùng khép kín (Polygon)"
    - g.isValid()      -> nếu không: "Ranh giới bị tự cắt, vui lòng vẽ lại"
    - phải chứa điểm tâm -> nếu không: "Toạ độ hotspot phải nằm trong ranh giới đã vẽ"
    hotspot.setBoundary((Polygon) g);
} else {
    hotspot.setBoundary(null);   // xoá polygon khi curator bỏ trống
}
if (request.getRadiusMeters() == null) hotspot.setRadiusMeters(DEFAULT_RADIUS_METERS);
```

Ràng buộc "tâm phải nằm trong polygon" quan trọng: nó giữ `findNearbyHotspotsWithStatus` (đang dùng `location`) nhất quán với vùng check-in.

### 9. API xem trước khoảng cách

Cho phép mobile bật/tắt nút "Check-in" theo thời gian thực đúng như Google, thay vì bấm rồi mới báo lỗi.

[HotspotController.java](src/main/java/org/sep490/backend/module/content/controller/HotspotController.java) — **không** đặt ở đây; đặt trong `UserHotspotProgressController` vì logic thuộc module exploration:

```java
GET /api/v1/user-hotspot-progress/eligibility
    ?hotspotId=..&latitude=..&longitude=..&accuracy=..
```

Trả `CheckInEligibilityResponse { boolean eligible, double distanceMeters, double requiredMeters, boolean alreadyCheckedIn, String message }`.

Dùng lại đúng method `evaluate(...)` ở mục 5 — không nhân đôi logic. Endpoint này `@Transactional(readOnly = true)`, không ghi gì.

### 10. Script migration

Tạo `migration/2026-08-05-hotspot-checkin-zone.sql`, theo đúng convention 2 file có sẵn (header tiếng Việt giải thích bối cảnh + thứ tự chạy). `ddl-auto: update` sẽ tự thêm cột nhưng **không backfill và không thêm constraint** cho cột đã tồn tại:

```sql
-- PHẦN 0: phòng khi app chưa boot
ALTER TABLE hotspots ADD COLUMN IF NOT EXISTS radius_meters INTEGER;
ALTER TABLE hotspots ADD COLUMN IF NOT EXISTS boundary geometry(Polygon, 4326);

-- PHẦN 1: backfill
UPDATE hotspots SET radius_meters = 50 WHERE radius_meters IS NULL;

-- PHẦN 2: ràng buộc
ALTER TABLE hotspots ALTER COLUMN radius_meters SET DEFAULT 50;
ALTER TABLE hotspots ALTER COLUMN radius_meters SET NOT NULL;
ALTER TABLE hotspots ADD CONSTRAINT chk_hotspot_radius
    CHECK (radius_meters BETWEEN 20 AND 5000);

-- PHẦN 3: index không gian cho boundary
CREATE INDEX IF NOT EXISTS idx_hotspots_boundary ON hotspots USING GIST (boundary);

-- PHẦN 4: đối chiếu
SELECT COUNT(*) FILTER (WHERE boundary IS NOT NULL) AS co_polygon,
       COUNT(*) FILTER (WHERE radius_meters <> 50)  AS radius_tuy_chinh,
       COUNT(*) AS tong FROM hotspots;
```

### 11. Sửa unit test cho build xanh

[UserHotspotProgressServiceImplTest.java:117](src/test/java/org/sep490/backend/module/exploration/service/impl/UserHotspotProgressServiceImplTest.java#L117) assert **cứng chuỗi** `"...bán kính 50m để check-in"`. Thông báo lỗi giờ là động → test này **chắc chắn fail**.

Phạm vi tối thiểu (bạn không chọn mở rộng test):
- Đổi assert sang `assertTrue(ex.getMessage().contains("cần vào trong phạm vi"))`.
- Thêm `@Mock HotspotRepository hotspotRepository` vào test (service có dependency mới) — nếu thiếu, `@InjectMocks` để null và mọi test NPE.
- `hotspot()` helper: `radiusMeters` để null → `effectiveRadius` trả 50 → 4 test hiện có giữ nguyên ý nghĩa.

---

## Files thay đổi

| File | Thay đổi |
|---|---|
| `module/content/entity/Hotspot.java` | +2 field `radiusMeters`, `boundary` |
| `module/content/repository/HotspotRepository.java` | +4 native query PostGIS |
| `module/content/dto/request/HotspotRequest.java` | +`radiusMeters`, +`boundaryGeoJson` |
| `module/content/dto/response/HotspotResponse.java` | +`radiusMeters`, +`boundaryGeoJson` |
| `module/content/mapper/HotspotMapper.java` | +`@Mapping ignore` tường minh (3 method) |
| `module/content/service/impl/HotspotServiceImpl.java` | tách `validateHotspotRequest`, dùng ở cả `create` và `update`; parse polygon |
| `module/exploration/service/impl/CheckInPolicy.java` | **mới** — hằng số + helper |
| `module/exploration/service/impl/UserHotspotProgressServiceImpl.java` | thay 50.0 bằng `evaluate(...)` 3 lớp |
| `module/exploration/dto/request/UserHotspotProgressRequest.java` | +`accuracy` (optional) |
| `module/exploration/dto/response/CheckInEligibilityResponse.java` | **mới** |
| `module/exploration/controller/UserHotspotProgressController.java` | +`GET /eligibility` |
| `module/exploration/service/inter/UserHotspotProgressService.java` | +method `checkEligibility` |
| `migration/2026-08-05-hotspot-checkin-zone.sql` | **mới** |
| `test/.../UserHotspotProgressServiceImplTest.java` | sửa assert + thêm mock |

Tận dụng lại (không viết mới): `SpatialUtils.calculateDistanceInMeters`, `SpatialUtils.fromCoordinates`, pattern `ST_DWithin`/`ST_Within` trong `HotspotRepository`, `BusinessException` + `GlobalExceptionHandler`, `CheckInStatusApplier`.

---

## Verification

**1. Build & unit test**
```powershell
.\mvnw clean test -Dtest=UserHotspotProgressServiceImplTest
```
Cả 5 test case cũ phải xanh (hotspot không radius → mặc định 50m, hành vi cũ giữ nguyên).

**2. Boot app + chạy migration**
```powershell
docker compose up -d
.\mvnw spring-boot:run
```
Để Hibernate tự tạo cột, rồi chạy `migration/2026-08-05-hotspot-checkin-zone.sql`. Xác nhận cột tồn tại:
```sql
\d hotspots
SELECT hotspot_id, hotspot_name, radius_meters, ST_AsText(boundary) FROM hotspots LIMIT 5;
```

**3. Kịch bản A — tương thích ngược (hotspot cũ, không polygon)**
`POST /api/v1/user-hotspot-progress` với toạ độ cách hotspot ~100m, không gửi `accuracy`
→ 400, message chứa "Bạn đang cách 100m, cần vào trong phạm vi 50m".
Cách ~40m → 201 thành công. **Hành vi giống hệt trước khi sửa.**

**4. Kịch bản B — bán kính tuỳ chỉnh (đúng vấn đề bạn nêu)**
Tạo hotspot "khu du lịch" với `radiusMeters=800` qua `POST /api/v1/hotspots` (multipart).
Check-in từ điểm cách tâm 500m → **201 thành công** (trước đây bị từ chối).

**5. Kịch bản C — polygon**
Tạo hotspot với `boundaryGeoJson` là polygon phủ khu du lịch, `latitude/longitude` là tâm nằm trong polygon.
- Điểm bên trong polygon nhưng cách tâm 600m → **201 thành công**.
- Điểm ngoài polygon 200m → 400.
- Điểm ngoài polygon 30m + `accuracy=50` → **201** (sai số GPS bao phủ).
- Gửi GeoJSON hỏng (`{"type":"Polygon"}`) → 400 "Ranh giới không đúng định dạng GeoJSON", **không phải** lỗi SQL 500.
- Gửi polygon không chứa điểm tâm → 400 "Toạ độ hotspot phải nằm trong ranh giới đã vẽ".

**6. Kịch bản D — chống gian lận accuracy**
Gửi `accuracy=99999` từ điểm cách 5km → vẫn 400 (tolerance bị chặn trần ở 100m).

**7. API preview**
```
GET /api/v1/user-hotspot-progress/eligibility?hotspotId=1&latitude=..&longitude=..&accuracy=12
```
→ `{ eligible, distanceMeters, requiredMeters, alreadyCheckedIn }`. Giá trị `eligible` phải **khớp** với kết quả `POST` thực tế ở cùng toạ độ.

**8. Validate ở update (lỗ hổng đang có)**
`PUT /api/v1/hotspots/{id}` với toạ độ ở Thái Lan → 400 "Tọa độ của Hotspot phải thuộc lãnh thổ Việt Nam" (hiện tại đang **cho qua**).

---

## Ghi chú cho front-end (ngoài phạm vi repo này)

- **Mobile**: gửi thêm `accuracy` từ `Location.getCurrentPositionAsync().coords.accuracy`; poll `/eligibility` để bật/tắt nút check-in theo thời gian thực.
- **Web curator**: dùng Leaflet Draw / Mapbox GL Draw / Google Maps Drawing Library — cả ba xuất GeoJSON trực tiếp, gán thẳng vào form field `boundaryGeoJson`. Với địa điểm đơn giản chỉ cần nhập `radiusMeters`.
