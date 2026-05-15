package com.smartgrocery.backend.repository.jpa;
import com.smartgrocery.backend.entity.ReorderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReorderEventRepository extends JpaRepository<ReorderEvent, Long> {}
