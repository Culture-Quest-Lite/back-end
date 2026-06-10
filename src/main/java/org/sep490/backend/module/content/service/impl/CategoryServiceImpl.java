package org.sep490.backend.module.content.service.impl;

import io.swagger.v3.oas.annotations.servers.Server;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.sep490.backend.module.content.dto.request.CategoryRequest;
import org.sep490.backend.module.content.dto.response.CategoryResponse;
import org.sep490.backend.module.content.entity.Category;
import org.sep490.backend.module.content.mapper.CategoryMapper;
import org.sep490.backend.module.content.repository.CategoryRepository;
import org.sep490.backend.module.content.service.inter.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class CategoryServiceImpl implements CategoryService {

    CategoryRepository categoryRepository;
    CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryMapper.toEntity(request);
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = getById(id);
        category.setCategoryName(request.getCategoryName());
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category);
    }

    @Override
    public CategoryResponse getDetail(Long id) {
        Category category = getById(id);
        return categoryMapper.toResponse(category);
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = getById(id);
        categoryRepository.delete(category);
    }

    @Override
    public Category getById(Long id) {
        Category category = categoryRepository.getByCategoryId(id).orElseThrow(
                () -> new RuntimeException("Category not found"));
        return category;
    }
}
