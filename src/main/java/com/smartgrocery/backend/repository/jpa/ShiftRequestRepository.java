package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ShiftRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftRequestRepository extends JpaRepository<ShiftRequest, Long> {
    Optional<ShiftRequest> findByUser_IdAndWorkDate(Long userId, LocalDate workDate);
    List<ShiftRequest> findByUser_IdAndWorkDateBetween(Long userId, LocalDate start, LocalDate end);
    List<ShiftRequest> findByWorkDateBetween(LocalDate start, LocalDate end);
    List<ShiftRequest> findByStatusAndWorkDateBetween(String status, LocalDate start, LocalDate end);
}

