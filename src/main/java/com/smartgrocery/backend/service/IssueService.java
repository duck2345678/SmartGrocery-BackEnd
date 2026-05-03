package com.smartgrocery.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartgrocery.backend.dto.CreateIssueRequest;
import com.smartgrocery.backend.dto.IssueDto;
import com.smartgrocery.backend.dto.ResolveIssueRequest;
import com.smartgrocery.backend.dto.StaffSubstitutionOptionDto;
import com.smartgrocery.backend.entity.Issue;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.OrderItem;
import com.smartgrocery.backend.entity.ProductVariant;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.InventoryStockRepository;
import com.smartgrocery.backend.repository.IssueRepository;
import com.smartgrocery.backend.repository.OrderItemRepository;
import com.smartgrocery.backend.repository.OrderRepository;
import com.smartgrocery.backend.repository.ProductVariantRepository;
import com.smartgrocery.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public IssueDto create(User reporter, CreateIssueRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu payload");
        if (request.getOrderId() == null) throw new IllegalArgumentException("Thiếu orderId");
        if (request.getIssueType() == null || request.getIssueType().isBlank()) {
            throw new IllegalArgumentException("Thiếu issueType");
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderItem orderItem = null;
        if (request.getOrderItemId() != null) {
            orderItem = orderItemRepository.findById(request.getOrderItemId())
                    .orElseThrow(() -> new RuntimeException("Order item not found"));
            if (orderItem.getOrder() == null || !orderItem.getOrder().getId().equals(order.getId())) {
                throw new IllegalArgumentException("orderItemId không thuộc orderId");
            }
        }

        Issue issue = Issue.builder()
                .order(order)
                .orderItem(orderItem)
                .reporter(reporter)
                .issueType(request.getIssueType().trim())
                .status("OPEN")
                .details(request.getDetails())
                .build();
        Issue saved = issueRepository.save(issue);

        order.setStatus("ON_HOLD");
        order.setAssignee(null);
        order.setLeaseExpiresAt(null);
        orderRepository.save(order);

        try {
            List<User> admins = userRepository.findByRole_Name("ADMIN");
            notificationService.notifyStaff(
                    "Sự cố đơn hàng: " + order.getOrderNumber(),
                    "Nhân viên " + (reporter.getFullName() != null ? reporter.getFullName() : reporter.getEmail()) + " đã báo sự cố cho đơn " + order.getOrderNumber(),
                    "ISSUE_REPORTED",
                    Map.of("route", "/(staff)/issues", "type", "ISSUE_REPORTED"),
                    admins
            );
        } catch (Exception ignored) {
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<IssueDto> myIssues(User reporter) {
        return issueRepository.findTop200ByReporter_IdOrderByCreatedAtDesc(reporter.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<IssueDto> byOrder(Long orderId) {
        return issueRepository.findTop200ByOrder_IdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<IssueDto> openIssues() {
        return issueRepository.findTop200ByStatusOrderByCreatedAtDesc("OPEN").stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public IssueDto getById(Long issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));
        return toDto(issue);
    }

    @Transactional(rollbackFor = Exception.class)
    public IssueDto resolveIssue(Long issueId, User admin, ResolveIssueRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu payload");
        String resolutionType = request.getResolutionType() != null ? request.getResolutionType().trim().toUpperCase() : "";
        if (resolutionType.isBlank()) throw new IllegalArgumentException("Thiếu resolutionType");

        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));
        if (!"OPEN".equalsIgnoreCase(issue.getStatus())) {
            throw new IllegalArgumentException("Issue đã được xử lý");
        }

        Order order = issue.getOrder();
        if (order == null) throw new RuntimeException("Issue chưa gắn order");
        OrderItem orderItem = issue.getOrderItem();
        JsonNode before = snapshotIssueState(issue, order, orderItem);

        switch (resolutionType) {
            case "SUBSTITUTE" -> {
                if (orderItem == null) throw new IllegalArgumentException("Issue không có order item để thay thế");
                if (request.getSubstituteProductId() == null) throw new IllegalArgumentException("Thiếu substituteProductId");

                ProductVariant substituteVariant = productVariantRepository.findById(request.getSubstituteProductId())
                        .orElseThrow(() -> new IllegalArgumentException("Sản phẩm thay thế không tồn tại"));

                orderItem.setVariant(substituteVariant);
                orderItem.setProductName(substituteVariant.getProduct() != null ? substituteVariant.getProduct().getName() : orderItem.getProductName());
                orderItem.setVariantName(substituteVariant.getVariantName());
                orderItem.setSku(substituteVariant.getSku());
                orderItem.setUnitPrice(substituteVariant.getNetPrice());
                orderItem.setSubtotal(safePrice(substituteVariant.getNetPrice()).multiply(BigDecimal.valueOf(safeQty(orderItem.getQuantity()))));
                orderItem.setTotalPrice(orderItem.getSubtotal());
                orderItem.setIsSubstituted(true);
                orderItem.setSubstitutedVariant(substituteVariant);
                orderItem.setSubstitutionReason(request.getResolutionNotes());
                orderItemRepository.save(orderItem);
            }
            case "PARTIAL" -> {
                if (orderItem == null) throw new IllegalArgumentException("Issue không có order item để xử lý partial");
                if (request.getPartialQuantity() == null) throw new IllegalArgumentException("Thiếu partialQuantity");
                int partialQty = request.getPartialQuantity();
                if (partialQty < 0 || partialQty > safeQty(orderItem.getQuantity())) {
                    throw new IllegalArgumentException("partialQuantity không hợp lệ");
                }
                orderItem.setQuantity(partialQty);
                BigDecimal line = safePrice(orderItem.getUnitPrice()).multiply(BigDecimal.valueOf(partialQty));
                orderItem.setSubtotal(line);
                orderItem.setTotalPrice(line);
                orderItemRepository.save(orderItem);
            }
            case "CANCEL_LINE" -> {
                if (orderItem == null) throw new IllegalArgumentException("Issue không có order item để hủy dòng");
                orderItem.setQuantity(0);
                orderItem.setSubtotal(BigDecimal.ZERO);
                orderItem.setTotalPrice(BigDecimal.ZERO);
                orderItemRepository.save(orderItem);
            }
            case "CANCEL_ORDER" -> {
                order.setStatus("CANCELLED");
                order.setAssignee(null);
                order.setLeaseExpiresAt(null);
            }
            default -> throw new IllegalArgumentException("resolutionType không hỗ trợ: " + resolutionType);
        }

        if (!"CANCEL_ORDER".equals(resolutionType)) {
            boolean release = Boolean.TRUE.equals(request.getReleaseOrder());
            if (release) {
                order.setStatus("PENDING");
                order.setAssignee(null);
                order.setLeaseExpiresAt(null);
            } else {
                order.setStatus("IN_PROGRESS");
                order.setAssignee(issue.getReporter());
                order.setLeaseExpiresAt(LocalDateTime.now().plusMinutes(10));
            }
            recalculateOrderTotals(order);
        }
        orderRepository.save(order);

        issue.setStatus("RESOLVED");
        issue.setDetails(mergeResolutionDetails(issue.getDetails(), request, admin));
        issueRepository.save(issue);

        String reason = request.getResolutionNotes() != null && !request.getResolutionNotes().trim().isBlank()
                ? request.getResolutionNotes().trim()
                : resolutionType;
        auditService.log(admin, "RESOLVE_ISSUE", "ISSUE", issueId, reason, before, snapshotIssueState(issue, order, orderItem));

        notifyReporterResolved(issue, request);
        return toDto(issue);
    }

    @Transactional(readOnly = true, transactionManager = "transactionManager")
    public List<StaffSubstitutionOptionDto> getSubstituteOptions(Long issueId, String keyword) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));
        if (issue.getOrderItem() == null || issue.getOrderItem().getVariant() == null) return List.of();

        OrderItem item = issue.getOrderItem();
        Long originalVariantId = item.getVariant().getId();
        BigDecimal originalUnitPrice = safePrice(item.getUnitPrice());

        List<ProductVariant> candidates;
        if (keyword != null && !keyword.isBlank()) {
            candidates = productVariantRepository.searchActiveForSubstitution(keyword.trim()).stream()
                    .limit(20)
                    .toList();
        } else {
            Long categoryId = item.getVariant().getProduct() != null && item.getVariant().getProduct().getCategory() != null
                    ? item.getVariant().getProduct().getCategory().getId()
                    : null;
            if (categoryId == null) return List.of();
            candidates = productVariantRepository
                    .findTop50ByProduct_Category_IdAndStatusAndNetPriceLessThanEqualOrderByNetPriceDesc(categoryId, "ACTIVE", originalUnitPrice);
        }

        List<Long> candidateIds = candidates.stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, originalVariantId))
                .toList();

        Map<Long, Integer> stockByVariantId = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            inventoryStockRepository.sumAvailableByVariantIds(candidateIds).forEach(x -> {
                Long id = x.getVariantId();
                Long total = x.getTotalAvailable();
                if (id != null) stockByVariantId.put(id, total != null ? total.intValue() : 0);
            });
        }

        List<StaffSubstitutionOptionDto> options = candidates.stream()
                .filter(v -> !Objects.equals(v.getId(), originalVariantId))
                .map(v -> {
                    int stock = stockByVariantId.getOrDefault(v.getId(), 0);
                    String name = v.getProduct() != null
                            ? v.getProduct().getName() + (v.getVariantName() != null ? " • " + v.getVariantName() : "")
                            : (v.getVariantName() != null ? v.getVariantName() : "Variant " + v.getId());
                    return StaffSubstitutionOptionDto.builder()
                            .variantId(v.getId())
                            .name(name)
                            .price(v.getNetPrice())
                            .stock(stock)
                            .isRecommended(false)
                            .build();
                })
                .filter(x -> x.getStock() != null && x.getStock() > 0)
                .sorted(Comparator.comparing(StaffSubstitutionOptionDto::getPrice, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        if (!options.isEmpty()) {
            options.get(0).setIsRecommended(true);
        }
        return options;
    }

    private JsonNode mergeResolutionDetails(JsonNode currentDetails, ResolveIssueRequest request, User admin) {
        ObjectNode detailsNode = currentDetails != null && currentDetails.isObject()
                ? ((ObjectNode) currentDetails).deepCopy()
                : objectMapper.createObjectNode();

        ObjectNode resolutionNode = objectMapper.createObjectNode();
        resolutionNode.put("resolutionType", request.getResolutionType());
        resolutionNode.put("releaseOrder", Boolean.TRUE.equals(request.getReleaseOrder()));
        if (request.getResolutionNotes() != null) resolutionNode.put("resolutionNotes", request.getResolutionNotes());
        if (request.getSubstituteProductId() != null) resolutionNode.put("substituteProductId", request.getSubstituteProductId());
        if (request.getPartialQuantity() != null) resolutionNode.put("partialQuantity", request.getPartialQuantity());
        if (admin != null && admin.getId() != null) resolutionNode.put("resolvedBy", admin.getId());
        resolutionNode.put("resolvedAt", LocalDateTime.now().toString());

        detailsNode.set("resolution", resolutionNode);
        return detailsNode;
    }

    private void recalculateOrderTotals(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrder_Id(order.getId());
        BigDecimal newSubtotal = items.stream()
                .map(i -> safePrice(i.getTotalPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setSubtotal(newSubtotal);
        order.setTotalAmount(newSubtotal.add(safePrice(order.getShippingFee())));
    }

    private ObjectNode snapshotIssueState(Issue issue, Order order, OrderItem item) {
        ObjectNode root = objectMapper.createObjectNode();

        if (issue != null) {
            ObjectNode i = objectMapper.createObjectNode();
            i.put("issueId", issue.getId() != null ? issue.getId() : 0);
            i.put("issueType", issue.getIssueType() != null ? issue.getIssueType() : "");
            i.put("status", issue.getStatus() != null ? issue.getStatus() : "");
            i.put("reporterId", issue.getReporter() != null && issue.getReporter().getId() != null ? issue.getReporter().getId() : null);
            i.put("orderId", issue.getOrder() != null && issue.getOrder().getId() != null ? issue.getOrder().getId() : null);
            i.put("orderItemId", issue.getOrderItem() != null && issue.getOrderItem().getId() != null ? issue.getOrderItem().getId() : null);
            root.set("issue", i);
        }

        if (order != null) {
            ObjectNode o = objectMapper.createObjectNode();
            o.put("orderId", order.getId() != null ? order.getId() : 0);
            o.put("orderNumber", order.getOrderNumber() != null ? order.getOrderNumber() : "");
            o.put("status", order.getStatus() != null ? order.getStatus() : "");
            o.put("assigneeId", order.getAssignee() != null && order.getAssignee().getId() != null ? order.getAssignee().getId() : null);
            o.put("leaseExpiresAt", order.getLeaseExpiresAt() != null ? order.getLeaseExpiresAt().toString() : null);
            o.put("subtotal", order.getSubtotal() != null ? order.getSubtotal().toPlainString() : "0");
            o.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : "0");
            o.put("createdAt", order.getCreatedAt() != null ? order.getCreatedAt().toString() : null);
            o.put("updatedAt", order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null);
            root.set("order", o);
        }

        if (item != null) {
            ObjectNode it = objectMapper.createObjectNode();
            it.put("orderItemId", item.getId() != null ? item.getId() : 0);
            it.put("variantId", item.getVariant() != null && item.getVariant().getId() != null ? item.getVariant().getId() : null);
            it.put("productName", item.getProductName() != null ? item.getProductName() : "");
            it.put("variantName", item.getVariantName() != null ? item.getVariantName() : null);
            it.put("quantity", item.getQuantity() != null ? item.getQuantity() : 0);
            it.put("pickedQuantity", item.getPickedQuantity() != null ? item.getPickedQuantity() : null);
            it.put("isSubstituted", Boolean.TRUE.equals(item.getIsSubstituted()));
            it.put("substitutedVariantId", item.getSubstitutedVariant() != null && item.getSubstitutedVariant().getId() != null ? item.getSubstitutedVariant().getId() : null);
            it.put("substitutionReason", item.getSubstitutionReason() != null ? item.getSubstitutionReason() : null);
            it.put("unitPrice", item.getUnitPrice() != null ? item.getUnitPrice().toPlainString() : "0");
            it.put("totalPrice", item.getTotalPrice() != null ? item.getTotalPrice().toPlainString() : "0");
            root.set("orderItem", it);
        }

        return root;
    }

    private void notifyReporterResolved(Issue issue, ResolveIssueRequest request) {
        try {
            if (issue.getReporter() == null) return;
            String title = "Sự cố đã được xử lý";
            String body = "Admin đã xử lý sự cố cho đơn " + (issue.getOrder() != null ? issue.getOrder().getOrderNumber() : "");
            if ("SUBSTITUTE".equalsIgnoreCase(request.getResolutionType()) && issue.getOrderItem() != null) {
                body = "Đã xử lý! Nhặt: " + issue.getOrderItem().getProductName();
            }
            notificationService.sendNotification(
                    issue.getReporter(),
                    title,
                    body,
                    "ISSUE_RESOLVED",
                    Map.of(
                            "type", "ISSUE_RESOLVED",
                            "orderId", issue.getOrder() != null && issue.getOrder().getId() != null ? issue.getOrder().getId().toString() : "",
                            "issueId", issue.getId() != null ? issue.getId().toString() : ""
                    )
            );
        } catch (Exception ignored) {
        }
    }

    private int safeQty(Integer qty) {
        return qty != null ? qty : 0;
    }

    private BigDecimal safePrice(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private IssueDto toDto(Issue i) {
        return IssueDto.builder()
                .id(i.getId())
                .orderId(i.getOrder() != null ? i.getOrder().getId() : null)
                .orderItemId(i.getOrderItem() != null ? i.getOrderItem().getId() : null)
                .reporterId(i.getReporter() != null ? i.getReporter().getId() : null)
                .reporterName(i.getReporter() != null ? i.getReporter().getFullName() : null)
                .issueType(i.getIssueType())
                .status(i.getStatus())
                .details(i.getDetails())
                .createdAt(i.getCreatedAt())
                .build();
    }
}
