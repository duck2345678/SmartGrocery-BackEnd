package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.ProductReviewDto;
import com.smartgrocery.backend.entity.ProductReview;
import com.smartgrocery.backend.repository.jpa.ProductReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class ReviewService {

    @Autowired
    private ProductReviewRepository productReviewRepository;

    public List<ProductReviewDto> getAllReviews() {
        return productReviewRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public void deleteReview(Long reviewId) {
        productReviewRepository.deleteById(reviewId);
    }

    private ProductReviewDto mapToDto(ProductReview r) {
        return ProductReviewDto.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getFullName())
                .productId(r.getProduct().getId())
                .productName(r.getProduct().getName())
                .rating(r.getRating())
                .content(r.getContent())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
