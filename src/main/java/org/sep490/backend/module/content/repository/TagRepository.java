package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long>, JpaSpecificationExecutor<Tag> {
    Optional<Tag> findByTagName(String tagName);
    boolean existsByTagNameIgnoreCase(String tagName);
    boolean existsByTagNameIgnoreCaseAndTagIdNot(String tagName, Long tagId);
}

