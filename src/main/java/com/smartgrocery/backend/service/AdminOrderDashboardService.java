package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminOrderDashboardSummaryDto;
import com.smartgrocery.backend.dto.AdminOrderSparklinePointDto;
import com.smartgrocery.backend.dto.AdminOrderSummaryDto;
import com.smartgrocery.backend.entity.Order;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.OrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderDashboardService {

    private static final String DELIVERED = "DELIVERED";
    private static final String PENDING = "PENDING";

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryDto> listRecentOrders(
            int page,
            int size,
            String search,
            String status,
            LocalDate from,
            LocalDate to,
            String sortBy,
            String sortDir
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        Sort sort = Sort.by(sortDirection(sortDir), normalizeSortBy(sortBy));
        return orderRepository.findAll(buildOrderSpec(search, status, from, to), PageRequest.of(safePage, safeSize, sort))
                .map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public Page<AdminOrderSummaryDto> listRecentOrders(int page, int size) {
        return listRecentOrders(page, size, null, null, null, null, "createdAt", "desc");
    }

    @Transactional(readOnly = true)
    public AdminOrderDashboardSummaryDto getDashboardSummary(LocalDate from, LocalDate to) {
        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusDays(29);
        if (startDate.isAfter(endDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }

        LocalDateTime fromAt = startDate.atStartOfDay();
        LocalDateTime toAt = endDate.plusDays(1).atStartOfDay();
        long total = orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromAt, toAt);
        long pending = orderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(PENDING, fromAt, toAt);
        long delivered = orderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(DELIVERED, fromAt, toAt);
        long cancelled = orderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("CANCELLED", fromAt, toAt)
                + orderRepository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan("CANCELED", fromAt, toAt);
        BigDecimal revenue = nvl(orderRepository.sumTotalAmountByStatusAndCreatedAtRange(DELIVERED, fromAt, toAt));
        BigDecimal grossMerchandiseValue = nvl(orderRepository.sumSubtotalByStatusAndCreatedAtRange(DELIVERED, fromAt, toAt));
        BigDecimal discountTotal = nvl(orderRepository.sumDiscountAmountByStatusAndCreatedAtRange(DELIVERED, fromAt, toAt));
        BigDecimal shippingFeeTotal = nvl(orderRepository.sumShippingFeeByStatusAndCreatedAtRange(DELIVERED, fromAt, toAt));
        BigDecimal netRevenue = grossMerchandiseValue.subtract(discountTotal).subtract(shippingFeeTotal).max(BigDecimal.ZERO);

        long days = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);
        LocalDate previousEndDate = startDate.minusDays(1);
        LocalDate previousStartDate = previousEndDate.minusDays(days - 1);
        LocalDateTime previousFromAt = previousStartDate.atStartOfDay();
        LocalDateTime previousToAt = previousEndDate.plusDays(1).atStartOfDay();
        BigDecimal previousRevenue = nvl(orderRepository.sumTotalAmountByStatusAndCreatedAtRange(DELIVERED, previousFromAt, previousToAt));
        BigDecimal revenueGrowthRate = percentChange(revenue, previousRevenue);
        BigDecimal cancellationRate = total > 0
                ? BigDecimal.valueOf(cancelled)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Long> statusCounts = orderRepository.countGroupedByStatusAndCreatedAtRange(fromAt, toAt).stream()
                .collect(Collectors.toMap(
                        p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN",
                        p -> p.getCount() != null ? p.getCount() : 0L,
                        (left, right) -> Long.valueOf(left.longValue() + right.longValue()),
                        LinkedHashMap::new
                ));

        List<Order> deliveredOrders = orderRepository.findRevenueOrdersByStatusAndCreatedAtRange(DELIVERED, fromAt, toAt);
        Map<LocalDate, BigDecimal> revenueByDate = new LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            revenueByDate.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusDays(1);
        }
        for (Order order : deliveredOrders) {
            if (order.getCreatedAt() == null) continue;
            LocalDate key = order.getCreatedAt().toLocalDate();
            revenueByDate.computeIfPresent(key, (ignored, value) -> value.add(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO));
        }

        List<AdminOrderSparklinePointDto> sparkline = revenueByDate.entrySet().stream()
                .map(e -> AdminOrderSparklinePointDto.builder()
                        .date(e.getKey())
                        .revenue(e.getValue())
                        .build())
                .toList();

        return AdminOrderDashboardSummaryDto.builder()
                .total(total)
                .pending(pending)
                .deliveredCount(delivered)
                .cancelledCount(cancelled)
                .revenue(revenue)
                .previousRevenue(previousRevenue)
                .revenueGrowthRate(revenueGrowthRate)
                .grossMerchandiseValue(grossMerchandiseValue)
                .discountTotal(discountTotal)
                .shippingFeeTotal(shippingFeeTotal)
                .netRevenue(netRevenue)
                .cancellationRate(cancellationRate)
                .statusCounts(statusCounts)
                .sparkline(sparkline)
                .build();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return nvl(current)
                .subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private AdminOrderSummaryDto toSummaryDto(Order order) {
        User customer = order.getUser();
        return AdminOrderSummaryDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .customerId(customer != null ? customer.getId() : null)
                .customerName(customer != null ? customer.getFullName() : null)
                .customerPhone(customer != null ? customer.getPhone() : null)
                .build();
    }

    private Specification<Order> buildOrderSpec(String search, String status, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();

            String normalizedSearch = search != null ? search.trim().toLowerCase() : "";
            if (!normalizedSearch.isBlank()) {
                Join<Order, User> user = root.join("user", JoinType.LEFT);
                String pattern = "%" + normalizedSearch + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("orderNumber")), pattern),
                        cb.like(cb.lower(user.get("fullName")), pattern),
                        cb.like(cb.lower(user.get("phone")), pattern)
                ));
            }

            String normalizedStatus = status != null ? status.trim().toUpperCase() : "";
            if (!normalizedStatus.isBlank() && !"ALL".equals(normalizedStatus)) {
                predicate = cb.and(predicate, cb.equal(cb.upper(root.get("status")), normalizedStatus));
            }

            LocalDateTime fromAt = from != null ? from.atStartOfDay() : null;
            LocalDateTime toAt = to != null ? to.plusDays(1).atStartOfDay() : null;
            if (fromAt != null && toAt != null && fromAt.isAfter(toAt)) {
                LocalDateTime tmp = fromAt;
                fromAt = toAt;
                toAt = tmp;
            }
            if (fromAt != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), fromAt));
            }
            if (toAt != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), toAt));
            }
            return predicate;
        };
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null) return "createdAt";
        return switch (sortBy) {
            case "orderNumber", "status", "totalAmount", "createdAt" -> sortBy;
            default -> "createdAt";
        };
    }

    private Sort.Direction sortDirection(String sortDir) {
        return "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
