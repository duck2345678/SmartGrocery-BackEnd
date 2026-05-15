package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.ShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {
    Optional<ShiftSchedule> findByUser_IdAndWorkDate(Long userId, LocalDate workDate);
    List<ShiftSchedule> findByUser_IdAndWorkDateBetween(Long userId, LocalDate start, LocalDate end);
    List<ShiftSchedule> findByWorkDateBetween(LocalDate start, LocalDate end);

    interface ShiftTypeCount {
        LocalDate getWorkDate();
        String getShiftType();
        long getTotal();
    }

    @Query("""
            select s.workDate as workDate,
                   s.shiftType as shiftType,
                   count(s.id) as total
            from ShiftSchedule s
            where s.workDate between :from and :to
              and s.shiftType in :shiftTypes
            group by s.workDate, s.shiftType
            """)
    List<ShiftTypeCount> countByDateAndShiftTypeRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("shiftTypes") List<String> shiftTypes
    );
}
