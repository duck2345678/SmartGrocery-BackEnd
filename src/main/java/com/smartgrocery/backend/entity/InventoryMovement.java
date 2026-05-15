package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="inventory_movements")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="warehouse_id") private Warehouse warehouse;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private String movementType;
    private Integer quantity;
    private String referenceType;
    private Long referenceId;
    @ManyToOne @JoinColumn(name="created_by") private User createdBy;
    @CreationTimestamp private LocalDateTime createdAt;
}
