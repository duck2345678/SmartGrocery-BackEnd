package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="purchase_order_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne @JoinColumn(name="purchase_order_id") private PurchaseOrder purchaseOrder;
    @ManyToOne @JoinColumn(name="variant_id") private ProductVariant variant;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
