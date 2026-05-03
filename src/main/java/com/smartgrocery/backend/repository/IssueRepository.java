package com.smartgrocery.backend.repository;

import com.smartgrocery.backend.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {
    List<Issue> findTop200ByReporter_IdOrderByCreatedAtDesc(Long reporterId);
    List<Issue> findTop200ByOrder_IdOrderByCreatedAtDesc(Long orderId);
    List<Issue> findTop200ByStatusOrderByCreatedAtDesc(String status);
}
