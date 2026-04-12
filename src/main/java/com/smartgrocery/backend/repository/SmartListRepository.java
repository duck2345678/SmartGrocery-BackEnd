package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.SmartList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmartListRepository extends JpaRepository<SmartList, Long> {
    List<SmartList> findByUser_Id(Long userId);
    List<SmartList> findByUser_IdAndType(Long userId, String type);
}
