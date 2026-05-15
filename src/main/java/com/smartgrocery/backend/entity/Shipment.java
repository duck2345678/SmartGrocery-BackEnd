package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity @Table(name="shipments")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Shipment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne 
    @JoinColumn(name="order_id") 
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private Order order;
    private String carrierName;
    private String trackingCode;
    private String shipmentStatus;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
