package com.smartgrocery.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderDispatchService orderDispatchService;

    @EventListener
    public void onStaffCheckedIn(StaffCheckedInEvent event) {
        try {
            int assigned = orderDispatchService.dispatchPendingOrdersNow();
            log.info("Re-dispatched {} pending orders after staff {} checked in", assigned, event.staffId());
        } catch (Exception e) {
            log.warn("Failed to re-dispatch orders after check-in: {}", e.getMessage());
        }
    }
}
