package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.Neo4jHealthDto;
import com.smartgrocery.backend.dto.Neo4jSyncDto;
import com.smartgrocery.backend.dto.TopQueryDto;
import com.smartgrocery.backend.entity.Product;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.graph.ProductNode;
import com.smartgrocery.backend.repository.ChatMessageRepository;
import com.smartgrocery.backend.repository.ProductRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.graph.ProductNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAiService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductNodeRepository productNodeRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Qualifier("neo4jTransactionManager")
    private final PlatformTransactionManager neo4jTransactionManager;

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Neo4jHealthDto neo4jHealth() {
        try {
            long count = productNodeRepository.count();
            return Neo4jHealthDto.builder()
                    .ok(true)
                    .productNodeCount(count)
                    .checkedAt(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            return Neo4jHealthDto.builder()
                    .ok(false)
                    .productNodeCount(0)
                    .checkedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public Neo4jSyncDto neo4jSync() {
        List<Product> products = productRepository.findAll();
        TransactionTemplate tt = new TransactionTemplate(neo4jTransactionManager);
        Long synced = tt.execute(status -> {
            long count = 0;
            for (Product p : products) {
                if (p == null || p.getId() == null) continue;
                Double price = null;
                try {
                    List<ProductVariant> variants = productVariantRepository.findByProduct_Id(p.getId());
                    if (!variants.isEmpty() && variants.get(0).getNetPrice() != null) {
                        price = variants.get(0).getNetPrice().doubleValue();
                    }
                } catch (Exception ignored) {
                }

                ProductNode node = ProductNode.builder()
                        .productId(p.getId())
                        .name(p.getName())
                        .price(price)
                        .build();
                productNodeRepository.save(node);
                count++;
            }
            return count;
        });

        return Neo4jSyncDto.builder()
                .ok(true)
                .syncedCount(synced != null ? synced : 0)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<TopQueryDto> topQueries(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return chatMessageRepository.topUserQueries(PageRequest.of(0, safeLimit)).stream()
                .map(row -> {
                    String q = row != null && row.length > 0 ? String.valueOf(row[0]) : "";
                    long c = 0;
                    if (row != null && row.length > 1 && row[1] != null) {
                        try {
                            c = Long.parseLong(String.valueOf(row[1]));
                        } catch (Exception ignored) {
                            c = 0;
                        }
                    }
                    return TopQueryDto.builder().query(q).count(c).build();
                })
                .toList();
    }
}
