package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findByNameContainingIgnoreCase(String name);
    Optional<Product> findByProductCode(String productCode);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Product> findByNameContainingIgnoreCaseAndCategoryId(String name, Long categoryId, Pageable pageable);
    
    long countByStatus(String status);
    long countByStatusNot(String status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Product p SET p.status = 'ACTIVE' WHERE p.status = 'HIDDEN'")
    int bulkActivateHidden();

    List<Product> findByStatus(String status);
    List<Product> findTop15ByStatusAndIsStapleTrueOrderByIdAsc(String status);
    List<Product> findTop20ByStatusOrderByIdAsc(String status);

    @Query("select p from Product p left join fetch p.category where p.status = 'ACTIVE'")
    List<Product> findActiveWithCategory();

    @Query("select p from Product p left join fetch p.category where p.id in :ids")
    List<Product> findAllByIdWithCategory(@org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
