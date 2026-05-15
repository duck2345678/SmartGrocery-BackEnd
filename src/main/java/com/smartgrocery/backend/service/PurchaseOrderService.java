package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.PurchaseOrderDto;
import com.smartgrocery.backend.dto.PurchaseOrderItemDto;
import com.smartgrocery.backend.entity.*;
import com.smartgrocery.backend.repository.jpa.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class PurchaseOrderService {

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private PurchaseOrderItemRepository purchaseOrderItemRepository;


    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private InventoryStockRepository inventoryStockRepository;

    public List<PurchaseOrderDto> getAll() {
        return purchaseOrderRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public PurchaseOrderDto createOrder(PurchaseOrderDto dto) {
        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber("PO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status("DRAFT")
                .totalAmount(BigDecimal.ZERO)
                .build();

        po = purchaseOrderRepository.save(po);

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItemDto itemDto : dto.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemDto.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Variant not found"));
            
            BigDecimal lineTotal = itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            total = total.add(lineTotal);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .variant(variant)
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .subtotal(lineTotal)
                    .build();
            purchaseOrderItemRepository.save(item);
        }

        po.setTotalAmount(total);
        return mapToDto(purchaseOrderRepository.save(po));
    }

    public PurchaseOrderDto receiveOrder(Long poId, Long warehouseId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("PO not found"));
        
        if (!"DRAFT".equals(po.getStatus())) {
            throw new RuntimeException("PO is already " + po.getStatus());
        }

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(poId);

        for (PurchaseOrderItem item : items) {
            updateInventory(warehouse, item.getVariant(), item.getQuantity());
        }

        po.setStatus("RECEIVED");
        return mapToDto(purchaseOrderRepository.save(po));
    }

    private void updateInventory(Warehouse warehouse, ProductVariant variant, Integer quantity) {
        InventoryStock stock = inventoryStockRepository.findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                .orElseGet(() -> InventoryStock.builder()
                        .warehouse(warehouse)
                        .variant(variant)
                        .availableQuantity(0)
                        .reservedQuantity(0)
                        .build());
        
        stock.setAvailableQuantity(stock.getAvailableQuantity() + quantity);
        inventoryStockRepository.save(stock);
    }

    private PurchaseOrderDto mapToDto(PurchaseOrder po) {
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
        return PurchaseOrderDto.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .status(po.getStatus())
                .totalAmount(po.getTotalAmount())
                .createdAt(po.getCreatedAt())
                .items(items.stream().map(i -> PurchaseOrderItemDto.builder()
                        .id(i.getId())
                        .variantId(i.getVariant().getId())
                        .variantName(i.getVariant().getVariantName())
                        .productName(i.getVariant().getProduct().getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .subtotal(i.getSubtotal())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
