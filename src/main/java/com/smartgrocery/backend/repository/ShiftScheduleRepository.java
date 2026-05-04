package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.ShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {
    Optional<ShiftSchedule> findByUserIdAndWorkDate(Long userId, LocalDate workDate);
    List<ShiftSchedule> findByUserIdAndWorkDateBetween(Long userId, LocalDate start, LocalDate end);
}
