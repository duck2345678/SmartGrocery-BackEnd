package com.smartgrocery.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "smart_lists")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "smart_list_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "list_name", length = 150, nullable = false)
    private String name;

    @Column(name = "list_type", length = 30, nullable = false)
    @Builder.Default
    private String type = "SHOPPING"; // SHOPPING, MEAL_PLAN

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "smartList", cascade = CascadeType.ALL)
    private List<SmartListItem> items;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
