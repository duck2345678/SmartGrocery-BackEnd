package com.smartgrocery.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "unit_alias")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_canonical_id", nullable = false)
    private UnitCanonical unitCanonical;

    @Column(name = "alias_text_raw", nullable = false, length = 120)
    private String aliasTextRaw;

    @Column(name = "alias_text_norm", nullable = false, length = 120)
    private String aliasTextNorm;

    @Column(name = "locale", nullable = false, length = 12)
    @Builder.Default
    private String locale = "vi";

    @Column(name = "source", nullable = false, length = 20)
    @Builder.Default
    private String source = "manual";

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
