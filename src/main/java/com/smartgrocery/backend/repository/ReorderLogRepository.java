package com.smartgrocery.backend.repository;
import com.smartgrocery.backend.entity.ReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReorderLogRepository extends JpaRepository<ReorderLog, Long> {}