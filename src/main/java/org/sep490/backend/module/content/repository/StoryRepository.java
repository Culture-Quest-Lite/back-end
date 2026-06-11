package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface StoryRepository extends JpaRepository<Story, Long> {
    Collection<Story> findByHotspot_HotspotId(Long hotspotId);
}
