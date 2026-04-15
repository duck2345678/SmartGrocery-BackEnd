package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.entity.Category;
import com.smartgrocery.backend.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Category", description = "Quản lý danh mục sản phẩm")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Operation(summary = "Lấy toàn bộ danh mục sản phẩm")
    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }
}
