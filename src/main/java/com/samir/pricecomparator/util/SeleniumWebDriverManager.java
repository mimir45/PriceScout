package com.samir.pricecomparator.util;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SeleniumWebDriverManager {

    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 30;
    private static final int IMPLICIT_WAIT_SECONDS = 10;

    private final ConcurrentHashMap<Long, WebDriver> drivers = new ConcurrentHashMap<>();

    public WebDriver getDriver() {
        long threadId = Thread.currentThread().threadId();
        return drivers.computeIfAbsent(threadId, id -> createDriver());
    }

    private WebDriver createDriver() {
        log.info("Creating new Chrome WebDriver instance for thread {}", Thread.currentThread().threadId());

      ChromeOptions options = getChromeOptions();

      // Disable automation flags
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);

        // Set timeouts
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(IMPLICIT_WAIT_SECONDS));

        return driver;
    }

  private static ChromeOptions getChromeOptions() {
    ChromeOptions options = new ChromeOptions();

    // Headless mode (no GUI)
    options.addArguments("--headless=new"); // Use new headless mode
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");

    // Anti-detection: mimic real browser
    options.addArguments("--disable-blink-features=AutomationControlled");
    options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

    // Enhanced anti-bot evasion
    options.addArguments("--window-size=1920,1080"); // Realistic window size
    options.addArguments("--start-maximized");
    options.addArguments("--disable-web-security"); // Bypass CORS
    options.addArguments("--disable-features=IsolateOrigins,site-per-process");
    options.addArguments("--allow-running-insecure-content");
    options.addArguments("--disable-setuid-sandbox");
    options.addArguments("--disable-webgl");
    options.addArguments("--disable-popup-blocking");

    // Language and locale
    options.addArguments("--lang=en-US");
    options.addArguments("--accept-lang=en-US,en;q=0.9");

    // Performance optimizations
    options.addArguments("--disable-gpu");
    options.addArguments("--disable-extensions");
    options.addArguments("--disable-images"); // Don't load images for faster scraping
    options.addArguments("--blink-settings=imagesEnabled=false");

    // Page load strategy
    options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

    // Add preferences to hide automation
    options.setExperimentalOption("prefs", java.util.Map.of(
        "credentials_enable_service", false,
        "profile.password_manager_enabled", false,
        "profile.default_content_setting_values.notifications", 2
    ));

    return options;
  }

  /**
     * Close the WebDriver for the current thread
     */
    public void closeDriver() {
        long threadId = Thread.currentThread().threadId();
        WebDriver driver = drivers.remove(threadId);
        if (driver != null) {
            try {
                driver.quit();
                log.info("Closed WebDriver instance for thread {}", threadId);
            } catch (Exception e) {
                log.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
    }

    /**
     * Close all WebDriver instances (called on application shutdown)
     */
    @PreDestroy
    public void closeAllDrivers() {
        log.info("Shutting down all WebDriver instances");
        drivers.forEach((threadId, driver) -> {
            try {
                driver.quit();
                log.info("Closed WebDriver for thread {}", threadId);
            } catch (Exception e) {
                log.warn("Error closing WebDriver for thread {}: {}", threadId, e.getMessage());
            }
        });
        drivers.clear();
    }

    /**
     * Get the number of active WebDriver instances
     */
    public int getActiveDriverCount() {
        return drivers.size();
    }
}
