package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.OrderAssignmentDto;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderAssignment;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.OrderAssignmentRepository;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import com.smartgrocery.backend.repository.jpa.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(value = "transactionManager")
public class OrderAssignmentService {

    @Autowired
    private OrderAssignmentRepository orderAssignmentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    public List<OrderAssignmentDto> getAssignments(Long staffId) {
        return orderAssignmentRepository.findByStaff_Id(staffId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public OrderAssignmentDto assignOrder(Long orderId, Long staffId, String taskType) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new RuntimeException("Staff user not found"));

        OrderAssignment assignment = OrderAssignment.builder()
                .order(order)
                .staff(staff)
                .taskType(taskType)
                .status("ASSIGNED")
                .build();
        
        return mapToDto(orderAssignmentRepository.save(assignment));
    }

    public OrderAssignmentDto updateStatus(Long assignmentId, String status, String proofImageUrl) {
        OrderAssignment assignment = orderAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));
        
        assignment.setStatus(status);
        if (proofImageUrl != null) {
            assignment.setProofImageUrl(proofImageUrl);
        }
        if ("DONE".equals(status) || "COMPLETED".equals(status)) {
            assignment.setCompletedAt(LocalDateTime.now());
        }
        
        OrderAssignment savedAssignment = orderAssignmentRepository.save(assignment);

        // Notify Customer about status change
        try {
            User customer = savedAssignment.getOrder().getUser();
            if (customer != null) {
                String title = "";
                String body = "";
                if ("SHIPPING".equalsIgnoreCase(status)) {
                    title = "Đơn hàng đang được giao";
                    body = "Đơn hàng " + savedAssignment.getOrder().getOrderNumber() + " đang trên đường đến với bạn!";
                } else if ("DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                    title = "Giao hàng thành công";
                    body = "Đơn hàng " + savedAssignment.getOrder().getOrderNumber() + " đã được giao thành công. Chúc bạn ngon miệng!";
                }

                if (!title.isEmpty()) {
                    notificationService.sendNotification(customer, title, body, "ORDER_STATUS");
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the update
        }
        
        return mapToDto(savedAssignment);
    }

    private OrderAssignmentDto mapToDto(OrderAssignment a) {
        return OrderAssignmentDto.builder()
                .id(a.getId())
                .orderId(a.getOrder().getId())
                .orderCode(a.getOrder().getOrderNumber())
                .staffUserId(a.getStaff().getId())
                .staffName(a.getStaff().getFullName())
                .taskType(a.getTaskType())
                .status(a.getStatus())
                .proofImageUrl(a.getProofImageUrl())
                .assignedAt(a.getAssignedAt())
                .completedAt(a.getCompletedAt())
                .customerName(a.getOrder().getUser() != null ? a.getOrder().getUser().getFullName() : "Khách vãng lai")
                .customerPhone(a.getOrder().getUser() != null ? a.getOrder().getUser().getPhone() : "")
                .deliveryAddress(a.getOrder().getAddress() != null ? 
                    a.getOrder().getAddress().getStreetAddress() + ", " + a.getOrder().getAddress().getCity() : "")
                .totalItems(a.getOrder().getOrderItems() != null ? a.getOrder().getOrderItems().size() : 0)
                .build();
    }
}
