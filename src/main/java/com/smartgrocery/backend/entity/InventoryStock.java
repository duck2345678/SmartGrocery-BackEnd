package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="inventory_stocks")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne @JoinColumn(name="warehouse_id") private Warehouse warehouse;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
