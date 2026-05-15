package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findByUser_IdAndWorkDate(Long userId, LocalDate workDate);
    Optional<AttendanceRecord> findByUser_IdAndWorkDateAndBlockNumber(Long userId, LocalDate workDate, int blockNumber);
    Optional<AttendanceRecord> findByUser_IdAndWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(Long userId, LocalDate workDate);
    List<AttendanceRecord> findByUser_IdAndWorkDateBetween(Long userId, LocalDate start, LocalDate end);
    List<AttendanceRecord> findByWorkDateAndCheckInAtIsNotNullAndCheckOutAtIsNull(LocalDate workDate);
    List<AttendanceRecord> findByCheckOutAtIsNull();
}
