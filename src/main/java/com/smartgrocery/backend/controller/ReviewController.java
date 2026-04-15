package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.ProductReviewDto;
import com.smartgrocery.backend.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/reviews")
@Tag(name = "Admin - Review", description = "Quản lý Đánh giá sản phẩm")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Operation(summary = "Lấy tất cả đánh giá của khách hàng")
    @GetMapping
    public ResponseEntity<List<ProductReviewDto>> getAll() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @Operation(summary = "Xóa/Ẩn đánh giá vi phạm")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok().build();
    }
}
