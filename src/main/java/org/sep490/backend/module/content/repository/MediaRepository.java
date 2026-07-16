package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.story.storyId = :storyId")
    int findMaxDisplayOrderByStoryId(@Param("storyId") Long storyId);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.hotspot.hotspotId = :hotspotId")
    int findMaxDisplayOrderByHotspotId(@Param("hotspotId") Long hotspotId);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.post.postId = :postId")
    int findMaxDisplayOrderByPostId(@Param("postId") Long postId);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.partnerInfo.partnerInfoId = :partnerInfoId")
    int findMaxDisplayOrderByPartnerInfoId(@Param("partnerInfoId") Long partnerInfoId);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.voucher.voucherId = :voucherId")
    int findMaxDisplayOrderByVoucherId(@Param("voucherId") Long voucherId);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM Media m WHERE m.route.routeId = :routeId")
    int findMaxDisplayOrderByRouteId(@Param("routeId") Long routeId);
}
