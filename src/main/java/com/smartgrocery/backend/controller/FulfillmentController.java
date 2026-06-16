package com.smartgrocery.backend.controller;

import com.smartgrocery.backend.dto.OrderAssignmentDto;
import com.smartgrocery.backend.service.OrderAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/admin/fulfillment", "/api/v1/admin/fulfillment"})
@Tag(name = "Admin - Fulfillment", description = "Quản lý Soạn hàng & Giao hàng")
public class FulfillmentController {

    @Autowired
    private OrderAssignmentService orderAssignmentService;

    @Operation(summary = "Phân công đơn hàng cho nhân viên")
    @PostMapping("/assign")
    public ResponseEntity<OrderAssignmentDto> assign(@RequestParam Long orderId, @RequestParam Long staffId, @RequestParam String taskType) {
        return ResponseEntity.ok(orderAssignmentService.assignOrder(orderId, staffId, taskType));
    }

    @Operation(summary = "Lấy danh sách công việc của nhân viên")
    @GetMapping("/staff/{staffId}")
    public ResponseEntity<List<OrderAssignmentDto>> getStaffAssignments(@PathVariable Long staffId) {
        return ResponseEntity.ok(orderAssignmentService.getAssignments(staffId));
    }

    @Operation(summary = "Cập nhật trạng thái công việc")
    @PutMapping("/assignments/{assignmentId}/status")
    public ResponseEntity<OrderAssignmentDto> updateStatus(
            @PathVariable Long assignmentId, 
            @RequestParam String status,
            @RequestParam(required = false) String proofImageUrl) {
        return ResponseEntity.ok(orderAssignmentService.updateStatus(assignmentId, status, proofImageUrl));
    }
}
