package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.InventoryStockDto;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class InventoryService {

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    public List<InventoryStockDto> getAll() {
        return inventoryStockRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<InventoryStockDto> getByWarehouse(Long warehouseId) {
        return inventoryStockRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private InventoryStockDto mapToDto(InventoryStock s) {
        return InventoryStockDto.builder()
                .id(s.getId())
                .warehouseId(s.getWarehouse().getId())
                .warehouseName(s.getWarehouse().getName())
                .variantId(s.getVariant().getId())
                .variantName(s.getVariant().getVariantName())
                .productName(s.getVariant().getProduct().getName())
                .availableQuantity(s.getAvailableQuantity())
                .reservedQuantity(s.getReservedQuantity())
                .build();
    }
}
