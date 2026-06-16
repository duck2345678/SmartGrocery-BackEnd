package com.smartgrocery.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrderDashboardSummaryDto {
    private long total;
    private long pending;
    private long deliveredCount;
    private long cancelledCount;
    private BigDecimal revenue;
    private BigDecimal previousRevenue;
    private BigDecimal revenueGrowthRate;
    private BigDecimal grossMerchandiseValue;
    private BigDecimal discountTotal;
    private BigDecimal shippingFeeTotal;
    private BigDecimal netRevenue;
    private BigDecimal cancellationRate;
    private Map<String, Long> statusCounts;
    private List<AdminOrderSparklinePointDto> sparkline;
}
