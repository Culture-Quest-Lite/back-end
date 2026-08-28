package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.CheckInRadiusConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CheckInRadiusConfigRepository extends JpaRepository<CheckInRadiusConfig, Long> {

    // Bảng chỉ có 1 dòng; lấy dòng cũ nhất để tránh đọc nhầm nếu lỡ có dữ liệu thừa.
    @Query("SELECT c FROM CheckInRadiusConfig c ORDER BY c.configId ASC LIMIT 1")
    Optional<CheckInRadiusConfig> findCurrent();
}
