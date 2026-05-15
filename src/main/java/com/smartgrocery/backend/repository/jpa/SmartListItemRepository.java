package com.smartgrocery.backend.repository.jpa;

import com.smartgrocery.backend.entity.SmartListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmartListItemRepository extends JpaRepository<SmartListItem, Long> {
    List<SmartListItem> findBySmartList_Id(Long smartListId);

    // Alias used by SmartListService
    default List<SmartListItem> findBySmartListId(Long smartListId) {
        return findBySmartList_Id(smartListId);
    }
}
