package com.smartgrocery.backend.entity.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;
import java.util.List;

@Node("SemanticCache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticCacheNode {

    @Id
    private String questionHash; // MD5 hoặc SHA của câu hỏi

    private String originalQuestion;
    
    @Property("answer")
    private String cachedAnswer;

    // Vector embedding (TEXT-EMBEDDING-004)
    private List<Double> embedding;

    @Builder.Default
    private Double trustScore = 1.0;

    @Builder.Default
    private Integer useCount = 0;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastUsedAt;

    // Lưu các metadata để đánh giá độ tươi của cache
    private String intentLabel;
}
