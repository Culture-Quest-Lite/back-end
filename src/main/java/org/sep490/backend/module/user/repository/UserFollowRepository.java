package org.sep490.backend.module.user.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    @Query("SELECT uf.follower FROM UserFollow uf " +
            "WHERE uf.following.userId = :userId " +
            "AND EXISTS (SELECT 1 FROM UserFollow uf2 WHERE uf2.follower.userId = :userId AND uf2.following.userId = uf.follower.userId)")
    List<User> findMutualFollowers(@Param("userId") Long userId);
    @Query("SELECT uf.following.userId FROM UserFollow uf " +
            "WHERE uf.follower = :follower AND uf.following.userId IN :userIds")
    Set<Long> findFollowingIdsByFollowerAndFollowingIdIn(@Param("follower") User follower,
                                                         @Param("userIds") Collection<Long> userIds);
}
