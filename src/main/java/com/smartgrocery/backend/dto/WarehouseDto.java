package com.smartgrocery.backend.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WarehouseDto {
    private Long id;
    private String code;
    private String name;
    private String location;
}
