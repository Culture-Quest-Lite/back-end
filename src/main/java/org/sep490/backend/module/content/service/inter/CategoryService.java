package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.dto.request.CategoryRequest;
import org.sep490.backend.module.content.dto.response.CategoryResponse;
import org.sep490.backend.module.content.entity.Category;

import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    CategoryResponse update(Long id, CategoryRequest request);
    CategoryResponse getDetail(Long id);
    List<CategoryResponse> getAll();
    void delete(Long id);
    Category getById(Long id);
}
