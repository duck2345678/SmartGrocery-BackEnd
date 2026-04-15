package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.ShipmentDto;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.Shipment;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.ShipmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class ShipmentService {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    public List<ShipmentDto> getAll() {
        return shipmentRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ShipmentDto createShipment(ShipmentDto dto) {
        Order order = orderRepository.findById(dto.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Shipment shipment = Shipment.builder()
                .order(order)
                .carrierName(dto.getCarrierName())
                .trackingCode(dto.getTrackingCode())
                .shipmentStatus("PENDING")
                .build();
        
        return mapToDto(shipmentRepository.save(shipment));
    }

    public ShipmentDto updateStatus(Long shipmentId, String status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));
        
        shipment.setShipmentStatus(status);
        if ("SHIPPED".equals(status)) {
            shipment.setShippedAt(LocalDateTime.now());
        } else if ("DELIVERED".equals(status)) {
            shipment.setDeliveredAt(LocalDateTime.now());
        }
        
        return mapToDto(shipmentRepository.save(shipment));
    }

    private ShipmentDto mapToDto(Shipment s) {
        return ShipmentDto.builder()
                .id(s.getId())
                .orderId(s.getOrder().getId())
                .orderCode(s.getOrder().getOrderNumber())
                .carrierName(s.getCarrierName())
                .trackingCode(s.getTrackingCode())
                .shipmentStatus(s.getShipmentStatus())
                .shippedAt(s.getShippedAt())
                .deliveredAt(s.getDeliveredAt())
                .build();
    }
}
