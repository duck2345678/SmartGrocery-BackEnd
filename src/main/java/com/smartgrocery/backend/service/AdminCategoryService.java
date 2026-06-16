package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminCategoryUpsertRequest;
import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.repository.jpa.CategoryRepository;
import com.smartgrocery.backend.repository.jpa.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminCategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<Category> listAll() {
        return categoryRepository.findAll();
    }

    @Transactional(transactionManager = "transactionManager")
    public Category create(AdminCategoryUpsertRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiáº¿u payload");
        String code = request.getCategoryCode() != null ? request.getCategoryCode().trim() : "";
        String name = request.getName() != null ? request.getName().trim() : "";
        if (code.isBlank()) throw new IllegalArgumentException("Thiáº¿u categoryCode");
        if (name.isBlank()) throw new IllegalArgumentException("Thiáº¿u name");
        if (categoryRepository.findByCategoryCode(code).isPresent()) {
            throw new IllegalArgumentException("categoryCode Ä‘Ã£ tá»“n táº¡i");
        }

        Category parent = null;
        if (request.getParentCategoryId() != null) {
            parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("parentCategoryId khÃ´ng tá»“n táº¡i"));
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
        if (id == null) throw new IllegalArgumentException("Thiáº¿u id");
        if (request == null) throw new IllegalArgumentException("Thiáº¿u payload");

        Category c = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));

        if (request.getCategoryCode() != null && !request.getCategoryCode().trim().isBlank()) {
            String code = request.getCategoryCode().trim();
            categoryRepository.findByCategoryCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(c.getId())) {
                    throw new IllegalArgumentException("categoryCode Ä‘Ã£ tá»“n táº¡i");
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
            if (Boolean.FALSE.equals(request.getIsActive())) {
                ensureCategoryCanBeDeactivated(id);
            }
            c.setIsActive(request.getIsActive());
        }

        if (request.getParentCategoryId() != null) {
            if (request.getParentCategoryId().equals(id)) {
                throw new IllegalArgumentException("parentCategoryId khÃ´ng há»£p lá»‡");
            }
            Category parent = categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("parentCategoryId khÃ´ng tá»“n táº¡i"));
            c.setParentCategory(parent);
        }

        return categoryRepository.save(c);
    }

    @Transactional(transactionManager = "transactionManager")
    public Category deactivate(Long id) {
        if (id == null) throw new IllegalArgumentException("Thiáº¿u id");
        Category c = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        ensureCategoryCanBeDeactivated(id);
        c.setIsActive(false);
        return categoryRepository.save(c);
    }

    private void ensureCategoryCanBeDeactivated(Long id) {
        long productCount = productRepository.countByCategoryId(id);
        if (productCount > 0) {
            throw new IllegalArgumentException("Chỉ có thể vô hiệu danh mục chưa có sản phẩm");
        }
    }
}


