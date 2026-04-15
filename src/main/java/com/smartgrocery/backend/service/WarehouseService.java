package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.WarehouseDto;
import com.smartgrocery.backend.entity.Warehouse;
import com.smartgrocery.backend.repository.WarehouseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class WarehouseService {

    @Autowired
    private WarehouseRepository warehouseRepository;

    public List<WarehouseDto> getAll() {
        return warehouseRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public WarehouseDto create(WarehouseDto dto) {
        Warehouse w = Warehouse.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .location(dto.getLocation())
                .build();
        return mapToDto(warehouseRepository.save(w));
    }

    private WarehouseDto mapToDto(Warehouse w) {
        return WarehouseDto.builder()
                .id(w.getId())
                .code(w.getCode())
                .name(w.getName())
                .location(w.getLocation())
                .build();
    }
}
