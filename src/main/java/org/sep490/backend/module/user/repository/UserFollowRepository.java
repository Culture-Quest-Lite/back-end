package org.sep490.backend.module.user.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {
    boolean existsByFollowerAndFollowing(User follower, User following);

    Optional<UserFollow> findByFollowerAndFollowing(User follower, User following);

    long countByFollower(User follower);

    long countByFollowing(User following);

    List<UserFollow> findAllByFollowing(User following);

    List<UserFollow> findAllByFollower(User follower);
}
