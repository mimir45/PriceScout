package com.samir.pricecomparator.scheduler;

import com.samir.pricecomparator.service.scraper.ScraperOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scraping.enabled", havingValue = "true", matchIfMissing = true)
public class DailyScraperJob {

    private final ScraperOrchestrator scraperOrchestrator;

    @Scheduled(cron = "${scraping.cron:0 0 2 * * *}")
    public void executeDailyScrape() {
        log.info("=== Starting daily scraping job ===");

        try {
            scraperOrchestrator.scrapeAllShops();
            log.info("=== Daily scraping job completed successfully ===");
        } catch (Exception e) {
            log.error("=== Daily scraping job failed ===", e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void schedulerHealthCheck() {
        log.debug("Scraper scheduler is running. Next scrape at configured cron time.");
    }
}
