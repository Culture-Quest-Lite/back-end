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

    @Query("SELECT uf.following.userId FROM UserFollow uf " +
            "WHERE uf.follower = :follower AND uf.following.userId IN :userIds")
    Set<Long> findFollowingIdsByFollowerAndFollowingIdIn(@Param("follower") User follower,
                                                         @Param("userIds") Collection<Long> userIds);
}
