package org.sep490.backend.module.social.repository;

import org.sep490.backend.module.social.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
