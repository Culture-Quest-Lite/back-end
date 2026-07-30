package org.sep490.backend.module.user.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    boolean existsByFollowerAndFollowing(User follower, User following);

    Optional<UserFollow> findByFollowerAndFollowing(User follower, User following);

    long countByFollower(User follower);

    long countByFollowing(User following);

    List<UserFollow> findAllByFollowing(User following);

    List<UserFollow> findAllByFollower(User follower);

    @Query("SELECT uf.follower.userId FROM UserFollow uf " +
            "WHERE uf.following.userId = :leaderId " +
            "AND uf.follower.userId IN :memberIds " +
            "AND EXISTS (SELECT 1 FROM UserFollow uf2 WHERE uf2.follower.userId = :leaderId AND uf2.following.userId = uf.follower.userId)")
    List<Long> findMutualFollowerIds(@Param("leaderId") Long leaderId, @Param("memberIds") List<Long> memberIds);
}
