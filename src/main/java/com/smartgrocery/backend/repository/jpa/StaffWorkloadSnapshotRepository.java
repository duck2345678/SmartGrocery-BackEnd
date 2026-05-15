package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.StaffWorkloadSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffWorkloadSnapshotRepository extends JpaRepository<StaffWorkloadSnapshot, Long> {}
