package com.smartgrocery.backend.service;

import com.smartgrocery.backend.dto.AdminShiftRequestStatusRequest;
import com.smartgrocery.backend.dto.ShiftRequestCreateRequest;
import com.smartgrocery.backend.entity.ShiftRequest;
import com.smartgrocery.backend.entity.ShiftSchedule;
import com.smartgrocery.backend.entity.User;
import com.smartgrocery.backend.repository.jpa.ShiftRequestRepository;
import com.smartgrocery.backend.repository.jpa.ShiftScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftRequestServiceTest {

    @Mock private ShiftRequestRepository shiftRequestRepository;
    @Mock private ShiftScheduleRepository shiftScheduleRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private ShiftRequestService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "minDaysAhead", 1);
        ReflectionTestUtils.setField(service, "maxDaysAhead", 14);
    }

    private User user() {
        return User.builder().id(10L).fullName("Staff A").build();
    }

    @Test
    void createRejectsAdjacentGBlocks() {
        ShiftRequestCreateRequest req = ShiftRequestCreateRequest.builder()
                .workDate(LocalDate.now().plusDays(3))
                .shiftType("G")
                .selectedBlocks(List.of(1, 2))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.createOrUpdateRequest(user(), req));
    }

    @Test
    void createSavesSelectedBlocksForValidGShift() {
        ShiftRequestCreateRequest req = ShiftRequestCreateRequest.builder()
                .workDate(LocalDate.now().plusDays(3))
                .shiftType("G")
                .selectedBlocks(List.of(1, 4))
                .build();
        when(shiftScheduleRepository.findByUser_IdAndWorkDate(10L, req.getWorkDate())).thenReturn(Optional.empty());
        when(shiftRequestRepository.findByUser_IdAndWorkDate(10L, req.getWorkDate())).thenReturn(Optional.empty());
        when(shiftRequestRepository.save(any(ShiftRequest.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.createOrUpdateRequest(user(), req);
        assertEquals("1,4", dto.getSelectedBlocks());

        ArgumentCaptor<ShiftRequest> captor = ArgumentCaptor.forClass(ShiftRequest.class);
        verify(shiftRequestRepository).save(captor.capture());
        assertEquals("1,4", captor.getValue().getSelectedBlocks());
    }

    @Test
    void adminApproveCopiesSelectedBlocksToSchedule() {
        LocalDate workDate = LocalDate.now().plusDays(3);
        ShiftRequest sr = ShiftRequest.builder()
                .id(1L)
                .user(user())
                .workDate(workDate)
                .shiftType("G")
                .selectedBlocks("1,4")
                .status("PENDING")
                .build();
        when(shiftRequestRepository.findById(1L)).thenReturn(Optional.of(sr));
        when(shiftScheduleRepository.findByUser_IdAndWorkDate(10L, workDate)).thenReturn(Optional.empty());
        when(shiftScheduleRepository.save(any(ShiftSchedule.class))).thenAnswer(i -> i.getArgument(0));
        when(shiftRequestRepository.save(any(ShiftRequest.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.adminUpdateStatus(1L, AdminShiftRequestStatusRequest.builder().status("APPROVED").build());
        assertEquals("APPROVED", dto.getStatus());
        assertEquals("1,4", dto.getSelectedBlocks());
    }

    @Test
    void adminApproveRejectsGShiftWithoutBlocks() {
        LocalDate workDate = LocalDate.now().plusDays(3);
        ShiftRequest sr = ShiftRequest.builder()
                .id(1L)
                .user(user())
                .workDate(workDate)
                .shiftType("G")
                .status("PENDING")
                .build();
        when(shiftRequestRepository.findById(1L)).thenReturn(Optional.of(sr));

        assertThrows(IllegalArgumentException.class, () ->
                service.adminUpdateStatus(1L, AdminShiftRequestStatusRequest.builder().status("APPROVED").build()));
    }
}
