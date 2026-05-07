package com.smartgrocery.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceOrderDispatchListener {

    private final OrderDispatchService orderDispatchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStaffCheckedIn(StaffCheckedInEvent event) {
        int assigned = orderDispatchService.dispatchPendingOrdersNow();
        log.info("Redispatched pending orders after staff {} check-in: {} assigned", event.staffId(), assigned);
    }
}
