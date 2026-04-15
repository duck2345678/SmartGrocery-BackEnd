package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.ProductEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, Long> {}