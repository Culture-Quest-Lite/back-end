package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.content.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> getByCategoryId(Long categoryId);
}
