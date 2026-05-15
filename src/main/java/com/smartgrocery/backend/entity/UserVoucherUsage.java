package com.smartgrocery.backend.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="user_voucher_usages")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserVoucherUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne @JoinColumn(name="user_id") private User user;
    @ManyToOne @JoinColumn(name="voucher_id") private Voucher voucher;
    private Integer usedCount;
    private LocalDateTime updatedAt;
}
