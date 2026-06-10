package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Hotspot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotspotRepository extends JpaRepository<Hotspot, Long> {
}
