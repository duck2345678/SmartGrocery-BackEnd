package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletePickingRequest {
    private List<PickedItem> pickedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickedItem {
        private Long originalOrderItemId;
        private Integer actualQuantity;
        private Boolean isSubstituted;
        private Long substitutedVariantId;
        private BigDecimal substitutedPrice;
        private String reason;
    }
}

