package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminCategoryUpsertRequest;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<Category> listAll() {
        return categoryRepository.findAll();
    }

    @Transactional(transactionManager = "transactionManager")
    public Category create(AdminCategoryUpsertRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu payload");
        String code = request.getCategoryCode() != null ? request.getCategoryCode().trim() : "";
        String name = request.getName() != null ? request.getName().trim() : "";
        if (code.isBlank()) throw new IllegalArgumentException("Thiếu categoryCode");
        if (name.isBlank()) throw new IllegalArgumentException("Thiếu name");
        if (categoryRepository.findByCategoryCode(code).isPresent()) {
            throw new IllegalArgumentException("categoryCode đã tồn tại");
        }

        Category parent = null;
        if (request.getParentCategoryId() != null) {
            parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("parentCategoryId không tồn tại"));
        }

        Category c = Category.builder()
                .categoryCode(code)
                .name(name)
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE)
                .parentCategory(parent)
                .build();
        return categoryRepository.save(c);
    }

    @Transactional(transactionManager = "transactionManager")
    public Category update(Long id, AdminCategoryUpsertRequest request) {
        if (id == null) throw new IllegalArgumentException("Thiếu id");
        if (request == null) throw new IllegalArgumentException("Thiếu payload");

        Category c = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));

        if (request.getCategoryCode() != null && !request.getCategoryCode().trim().isBlank()) {
            String code = request.getCategoryCode().trim();
            categoryRepository.findByCategoryCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(c.getId())) {
                    throw new IllegalArgumentException("categoryCode đã tồn tại");
                }
            });
            c.setCategoryCode(code);
        }

        if (request.getName() != null && !request.getName().trim().isBlank()) {
            c.setName(request.getName().trim());
        }

        if (request.getDescription() != null) {
            c.setDescription(request.getDescription());
        }

        if (request.getSortOrder() != null) {
            c.setSortOrder(request.getSortOrder());
        }

        if (request.getIsActive() != null) {
            c.setIsActive(request.getIsActive());
        }

        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(id)) {
                throw new IllegalArgumentException("parentCategoryId không hợp lệ");
            }
            Category parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("parentCategoryId không tồn tại"));
            c.setParentCategory(parent);
        }

        return categoryRepository.save(c);
    }

    @Transactional(transactionManager = "transactionManager")
    public Category deactivate(Long id) {
        if (id == null) throw new IllegalArgumentException("Thiếu id");
        Category c = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        c.setIsActive(false);
        return categoryRepository.save(c);
    }
}

