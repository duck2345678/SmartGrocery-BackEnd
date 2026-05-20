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
@Table(name = "unit_canonical")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitCanonical {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_code", nullable = false, unique = true, length = 60)
    private String unitCode;

    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    @Column(name = "to_base_factor", nullable = false, precision = 14, scale = 6)
    private BigDecimal toBaseFactor;

    @Column(name = "base_unit_code", nullable = false, length = 60)
    private String baseUnitCode;

    @Column(name = "is_approximate", nullable = false)
    @Builder.Default
    private Boolean approximate = false;

    @Column(name = "default_mass_g", precision = 12, scale = 4)
    private BigDecimal defaultMassG;

    @Column(name = "default_volume_ml", precision = 12, scale = 4)
    private BigDecimal defaultVolumeMl;

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
