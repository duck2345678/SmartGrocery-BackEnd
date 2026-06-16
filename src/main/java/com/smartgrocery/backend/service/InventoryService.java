package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.InventoryStockDto;
import com.smartgrocery.backend.entity.InventoryStock;
import com.smartgrocery.backend.repository.jpa.InventoryStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class InventoryService {

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    public Page<InventoryStockDto> getAll(int page, int size, String search) {
        return inventoryStockRepository.findAllWithRelations(normalizeSearch(search), pageRequest(page, size))
                .map(this::mapToDto);
    }

    public Page<InventoryStockDto> getByWarehouse(Long warehouseId, int page, int size, String search) {
        return inventoryStockRepository.findByWarehouseIdWithRelations(warehouseId, normalizeSearch(search), pageRequest(page, size))
                .map(this::mapToDto);
    }

    public List<InventoryStockDto> getAll() {
        return inventoryStockRepository.findAllWithRelations().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<InventoryStockDto> getByWarehouse(Long warehouseId) {
        return inventoryStockRepository.findByWarehouseIdWithRelations(warehouseId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        return PageRequest.of(safePage, safeSize);
    }

    private String normalizeSearch(String search) {
        if (search == null) return null;
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
