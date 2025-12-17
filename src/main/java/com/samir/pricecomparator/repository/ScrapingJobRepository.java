package com.samir.pricecomparator.repository;

import com.samir.pricecomparator.entity.ScrapingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScrapingJobRepository extends JpaRepository<ScrapingJob, Long> {
    List<ScrapingJob> findByShopIdOrderByStartedAtDesc(Long shopId);
    List<ScrapingJob> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime after);
}
