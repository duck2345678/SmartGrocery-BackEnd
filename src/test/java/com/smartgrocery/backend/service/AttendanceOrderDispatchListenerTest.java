package com.smartgrocery.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceOrderDispatchListenerTest {

    @Mock private OrderDispatchService orderDispatchService;

    @Test
    void onStaffCheckedInTriggersRedispatch() {
        when(orderDispatchService.dispatchPendingOrdersNow()).thenReturn(2);
        AttendanceOrderDispatchListener listener = new AttendanceOrderDispatchListener(orderDispatchService);
        listener.onStaffCheckedIn(new StaffCheckedInEvent(1L));
        verify(orderDispatchService).dispatchPendingOrdersNow();
    }
}
