package com.smartgrocery.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDictionaryProjection {
    private Long productId;
    private String productName;
    private List<String> synonyms;
}
