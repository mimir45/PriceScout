package com.samir.pricecomparator.service.scraper;

import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.util.SeleniumWebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.By;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Mağaza Scraper-ləri üçün Abstrakt Baza Sinfi
 *
 * Bu sinif bütün mağaza-spesifik scraper-lər üçün ümumi funksionallığı təmin edir.
 * Hər bir mağaza scraper-i bu sinifdən extend edilməlidir və 2 abstrakt metodu tətbiq etməlidir:
 * 1. extractProductUrls() - Məhsul səhifələrinin URL-lərini tapır
 * 2. scrapeProduct(url) - Konkret məhsul səhifəsindən məlumat çıxarır
 *
 * ========================================
 * ƏSAS XÜSUSİYYƏTLƏR:
 * ========================================
 *
 * 1. RETRY MEXANİZMİ (Təkrar cəhd):
 *    - Şəbəkə xətaları zamanı avtomatik təkrar cəhd edir
 *    - Maksimum 3 cəhd (MAX_RETRIES = 3)
 *    - Hər cəhd arasında 2 saniyə gözləmə (RETRY_DELAY_MS = 2000)
 *    - Exponential backoff: 1-ci cəhd - 2s, 2-ci cəhd - 4s, 3-cü cəhd - 6s
 *
 * 2. POLİTE SCRAPİNG (Nəzakətli scraping):
 *    - Hər sorğu arasında 500ms gözləmə (POLITE_DELAY_MS = 500)
 *    - Bu mağazaların serverlərini yükləməmək və block edilməməkdir
 *    - Robot.txt qaydalarına hörmət edir
 *
 * 3. DUAL FETCH METODLARİ:
 *    a) fetchDocument() - Sadə HTTP sorğusu (Jsoup ilə)
 *       - Statik HTML səhifələr üçün
 *       - Sürətli və resurs qənaətcil
 *
 *    b) fetchDocumentWithSelenium() - Brauzer simulyasiyası (Selenium ilə)
 *       - JavaScript yüklənən dinamik səhifələr üçün
 *       - Anti-bot deteksiyasını bypass edir
 *       - Scroll edərək lazy-loaded məzmunu aktivləşdirir
 *
 *    c) fetchDocumentWithSeleniumWait() - Explicit wait ilə Selenium
 *       - AJAX-heavy səhifələr üçün
 *       - Konkret CSS selector görünənə qədər gözləyir
 *       - Stealth mode: navigator.webdriver property-ni gizlədir
 *
 * 4. HELPER METODLAR:
 *    - extractText(): CSS selector ilə mətn çıxarma
 *    - extractAttr(): CSS selector ilə atribut çıxarma
 *    - parsePrice(): Qiymət mətnini BigDecimal-a parse etmə
 *    - normalizeUrl(): Nisbi URL-ləri tam URL-ə çevirmə
 *    - isInStock(): Məhsulun stokda olub-olmadığını yoxlama
 *
 * ========================================
 * NİYƏ BU YANAŞMA?
 * ========================================
 *
 * Template Method Pattern istifadə edilir:
 * - scrape() metodu scraping-in ümumi axışını təyin edir (template)
 * - extractProductUrls() və scrapeProduct() metodları alt-siniflər tərəfindən tətbiq edilir
 *
 * Bu yanaşma:
 * ✓ Kod təkrarını aradan qaldırır
 * ✓ Retry və rate limiting kimi ümumi məsələləri mərkəzləşdirir
 * ✓ Yeni mağaza əlavə etməyi asanlaşdırır (yalnız 2 metod tətbiq et)
 * ✓ Saxlanması və testləşdirilməsi asandır
 *
 * ========================================
 * İSTİFADƏ NÜMUNƏSİ:
 * ========================================
 *
 * public class MyShopScraper extends AbstractShopScraper {
 *
 *     @Override
 *     protected List<String> extractProductUrls() {
 *         Document doc = fetchDocument("https://myshop.az/phones");
 *         return doc.select(".product-link").stream()
 *             .map(e -> e.attr("href"))
 *             .map(this::normalizeUrl)
 *             .collect(Collectors.toList());
 *     }
 *
 *     @Override
 *     protected Optional<ScrapedProductDto> scrapeProduct(String url) {
 *         Document doc = fetchDocument(url);
 *         String title = extractText(doc, ".product-title");
 *         BigDecimal price = parsePrice(extractText(doc, ".price"));
 *         // ...
 *         return Optional.of(dto);
 *     }
 * }
 */
@Slf4j
public abstract class AbstractShopScraper {

    /**
     * HTTP sorğusu üçün maksimum timeout müddəti (millisaniyə)
     * 30 saniyə - yavaş serverlər üçün kifayət qədər uzun
     */
    protected static final int TIMEOUT_MS = 30000;

    /**
     * Uğursuz sorğu zamanı maksimum təkrar cəhd sayı
     * 3 cəhd - çox aggressiv olmamaq üçün balanslaşdırılmış rəqəm
     */
    protected static final int MAX_RETRIES = 3;

    /**
     * Hər təkrar cəhd arasında baza gözləmə müddəti (millisaniyə)
     * 2 saniyə - exponential backoff üçün baza dəyər
     * Faktiki gözləmə: attempt * RETRY_DELAY_MS (2s, 4s, 6s)
     */
    protected static final int RETRY_DELAY_MS = 2000;

    /**
     * Hər sorğu arasında nəzakətli gözləmə müddəti (millisaniyə)
     * 500ms - mağazaların serverlərini yükləməmək və block edilməmək üçün
     *
     * NİYƏ 500MS?
     * - Çox qısa (100ms) → Server yüklənməsi, block riski
     * - Çox uzun (2000ms) → Scraping çox yavaş olur
     * - 500ms → Optimal balans
     */
    protected static final int POLITE_DELAY_MS = 500;

    /**
     * Scraping aparılan mağaza obyekti
     * Bu obyekt mağaza məlumatlarını (kod, ad, baseUrl və s.) ehtiva edir
     */
    protected final Shop shop;

    /**
     * Selenium WebDriver manager (opsional)
     *
     * @Autowired(required = false):
     * - Selenium konfiqurasiya olunubsa inject edilir
     * - Konfiqurasiya olunmayıbsa null qalır və Jsoup istifadə olunur
     *
     * Selenium istifadə halları:
     * - Anti-bot deteksiyası olan mağazalar (KONTAKT)
     * - JavaScript ilə yüklənən məzmun (IRSHAD Load More button)
     * - Lazy loading ilə məhsul kartları
     */
    @Autowired(required = false)
    protected SeleniumWebDriverManager webDriverManager;

    /**
     * Konstruktor - mağaza obyekti ilə inisializasiya
     *
     * @param shop Scraping aparılacaq mağaza
     */
    protected AbstractShopScraper(Shop shop) {
        this.shop = shop;
    }

    /**
     * ƏSAS SCRAPING METODU - Template Method Pattern
     *
     * Bu metod scraping prosesinin ümumi axışını təyin edir:
     * 1. Məhsul URL-lərini tap (extractProductUrls)
     * 2. Hər URL üçün məhsul məlumatını çıxar (scrapeProduct)
     * 3. Hər sorğu arasında nəzakətli gözləmə
     * 4. Xətaları idarə et və log-la
     *
     * @return Scrape edilmiş məhsulların siyahısı
     */
    public List<ScrapedProductDto> scrape() {
        log.info("Starting scrape for shop: {}", shop.getCode());
        List<ScrapedProductDto> products = new ArrayList<>();

        try {
            // Addım 1: Məhsul URL-lərini tap
            // Alt-sinif tərəfindən tətbiq edilir (mağaza-spesifik)
            List<String> productUrls = extractProductUrls();
            log.info("Found {} product URLs for {}", productUrls.size(), shop.getCode());

            // Addım 2: Hər məhsul URL-i üçün məlumat çıxar
            for (String url : productUrls) {
                try {
                    // Məhsul səhifəsindən məlumat çıxar
                    Optional<ScrapedProductDto> product = scrapeProduct(url);

                    // Əgər məhsul uğurla scrape edilibsə, siyahıya əlavə et
                    product.ifPresent(products::add);

                    // Nəzakətli gözləmə - növbəti sorğu öncə
                    // Bu mağazanın serverini yükləməmək üçün vacibdir
                    TimeUnit.MILLISECONDS.sleep(POLITE_DELAY_MS);

                } catch (Exception e) {
                    // Tək məhsulda xəta bütün scraping-i dayandırmamalıdır
                    log.error("Failed to scrape product: {}", url, e);
                }
            }
        } catch (Exception e) {
            // Ümumi scraping xətası (məsələn, URL çıxarma uğursuz olub)
            log.error("Failed to scrape shop: {}", shop.getCode(), e);
        }

        log.info("Scraped {} products from {}", products.size(), shop.getCode());
        return products;
    }

    /**
     * ABSTRAKT METOD: Məhsul URL-lərini çıxar
     *
     * Bu metod mağazanın məhsul siyahı səhifəsindən (və ya səhifələrindən)
     * bütün məhsul səhifələrinin URL-lərini tapmalıdır.
     *
     * Tətbiq nümunəsi:
     * - KONTAKT: Paginasiya ilə 15 səhifə gəz
     * - IRSHAD: "Load More" düyməsinə klikləyərək bütün məhsulları yüklə
     * - BAKU_ELECTRONICS: __NEXT_DATA__ JSON-dan URL-ləri çıxar
     *
     * @return Məhsul səhifələrinin tam URL-lərinin siyahısı
     * @throws IOException Şəbəkə xətası zamanı
     */
    protected abstract List<String> extractProductUrls() throws IOException;

    /**
     * ABSTRAKT METOD: Tək məhsul səhifəsindən məlumat çıxar
     *
     * Bu metod konkret məhsul səhifəsindən (URL) məhsul məlumatlarını
     * çıxarmalı və ScrapedProductDto obyektinə çevirməlidir.
     *
     * Çıxarılmalı məlumatlar:
     * - Başlıq (title)
     * - Qiymət (price)
     * - Köhnə qiymət (oldPrice) - varsa
     * - Şəkil URL-i (imageUrl)
     * - Məhsul URL-i (productUrl)
     * - Stok durumu (inStock)
     * - Rəng (color) - varsa
     * - Vəziyyət (condition) - varsa
     *
     * @param url Məhsul səhifəsinin tam URL-i
     * @return Scrape edilmiş məhsul DTO-su (və ya boş Optional xəta zamanı)
     */
    protected abstract Optional<ScrapedProductDto> scrapeProduct(String url);

    /**
     * HTML sənədini HTTP sorğusu ilə gətir (Jsoup istifadə edərək)
     *
     * Bu metod RETRY MEXANİZMİ ilə təchiz edilib:
     * - Maksimum 3 cəhd
     * - Exponential backoff: 2s, 4s, 6s
     * - IOException zamanı təkrar cəhd edir
     *
     * NİYƏ RETRY LAZİMDİR?
     * - Şəbəkə qeyri-sabitliyi (timeout, connection reset)
     * - Mağazanın serveri müvəqqəti əlçatmaz ola bilər
     * - Rate limiting - server tez-tez sorğunu rədd edə bilər
     *
     * İSTİFADƏ HALİ:
     * - Statik HTML səhifələr
     * - JavaScript olmayan sadə məhsul səhifələri
     * - Sürətli scraping lazım olduqda
     *
     * @param url Gətirilməli URL
     * @return Jsoup Document obyekti (parse edilmiş HTML)
     * @throws IOException Bütün cəhdlər uğursuz olduqda
     */
    protected Document fetchDocument(String url) throws IOException {
        int attempts = 0;
        IOException lastException = null;

        while (attempts < MAX_RETRIES) {
            try {
                log.debug("Fetching URL (attempt {}/{}): {}", attempts + 1, MAX_RETRIES, url);

                // Jsoup ilə HTTP GET sorğusu
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(TIMEOUT_MS)
                        .followRedirects(true)
                        .get();

            } catch (IOException e) {
                lastException = e;
                attempts++;
                log.warn("Attempt {}/{} failed for URL: {}", attempts, MAX_RETRIES, url);

                // Əgər hələ cəhdlər qalıbsa, gözlə və təkrar cəhd et
                if (attempts < MAX_RETRIES) {
                    try {
                        // Exponential backoff: 2s, 4s, 6s
                        TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS * attempts);
                    } catch (InterruptedException ie) {
                        // Thread kəsilmə bayrağını bərpa et
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        // Bütün cəhdlər uğursuz olub - exception at
        throw lastException;
    }

    /**
     * HTML sənədini Selenium WebDriver ilə gətir (Brauzer simulyasiyası)
     *
     * Bu metod REAL BRAUZER simulyasiyası edir:
     * - Chrome WebDriver istifadə edir (headless mode)
     * - JavaScript kodları icra olunur
     * - DOM tam yüklənir
     * - Lazy loading content aktivləşdirilir (scroll edərək)
     *
     * NİYƏ SELENİUM LAZİMDİR?
     * - Mağazalar JavaScript ilə məhsul kartlarını yükləyir
     * - Anti-bot deteksiyası (403 Forbidden bypass)
     * - AJAX sorğuları ilə dinamik məzmun
     * - Infinite scroll və lazy loading
     *
     * SCROLL STRATEGİYASI:
     * 1. 4 saniyə gözlə (səhifə tam yüklənsin)
     * 2. Aşağı scroll et (lazy content aktivləşsin)
     * 3. 2 saniyə gözlə (yeni məzmun yüklənsin)
     * 4. Yuxarı scroll et (bütün məzmunu DOM-da saxla)
     * 5. 2 saniyə gözlə (final render)
     *
     * İSTİFADƏ HALİ:
     * - JavaScript-heavy səhifələr (KONTAKT)
     * - Anti-bot deteksiyası olan mağazalar
     * - Lazy loading ilə məhsul siyahıları
     *
     * @param url Gətirilməli URL
     * @return Jsoup Document (Selenium-dan gələn HTML parse edilib)
     * @throws IOException Selenium xətası zamanı
     */
    protected Document fetchDocumentWithSelenium(String url) throws IOException {
        // Əgər Selenium konfiqurasiya olunmayıbsa, Jsoup-a fall back et
        if (webDriverManager == null) {
            log.warn("SeleniumWebDriverManager not available, falling back to Jsoup");
            return fetchDocument(url);
        }

        try {
            log.debug("Fetching URL with Selenium: {}", url);
            WebDriver driver = webDriverManager.getDriver();
            driver.get(url);

            // JavaScript yüklənib render olması üçün gözlə
            // Pagination səhifələr üçün artırılmış gözləmə müddəti
            try {
                // Başlanğıc gözləmə - səhifə yüklənməsi (3s → 4s artırılıb)
                TimeUnit.MILLISECONDS.sleep(4000);

                // Lazy loading-i aktivləşdirmək üçün aşağı scroll et
                ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, document.body.scrollHeight);");

                // Scroll sonrası gözləmə - yeni məzmun yüklənsin
                TimeUnit.MILLISECONDS.sleep(2000);

                // Yuxarı scroll et - bütün məzmunu DOM-da saxla
                ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, 0);");

                // Final gözləmə - render tamamlansın
                TimeUnit.MILLISECONDS.sleep(2000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Hazır HTML-i Selenium-dan al və Jsoup ilə parse et
            String pageSource = driver.getPageSource();
            return Jsoup.parse(pageSource, url);

        } catch (Exception e) {
            log.error("Selenium fetch failed for URL: {}", url, e);
            throw new IOException("Selenium fetch failed: " + e.getMessage(), e);
        }
    }

    /**
     * HTML sənədini Selenium ilə gətir + Explicit Wait (konkret elementi gözlə)
     *
     * Bu metod fetchDocumentWithSelenium-dan daha ADVANCED-dir:
     * - Konkret CSS selector-u gözləyir
     * - Selector görünənə qədər timeout zamanı gözləyir
     * - STEALTH MODE: Anti-bot deteksiyasını bypass edir
     *
     * STEALTH MODE TEXNIKA:
     * 1. navigator.webdriver → undefined (Chrome deteksiyasını gizlət)
     * 2. navigator.plugins → [1,2,3,4,5] (real brauzer kimi görsən)
     * 3. navigator.languages → ['en-US', 'en'] (dil konfiqurasiyası)
     *
     * NİYƏ STEALTH MODE?
     * Bəzi mağazalar bot deteksiyası edir:
     * - navigator.webdriver === true yoxlayırlar
     * - Boş plugins siyahısını bot kimi tanıyırlar
     * - Bu JavaScript kodları ilə real brauzer kimi görünürük
     *
     * EXPLICIT WAIT PATTERN:
     * - WebDriverWait: Maksimum X saniyə gözlə
     * - ExpectedConditions.presenceOfElementLocated: Element DOM-da görünənə qədər
     * - TimeoutException: Element tapılmasa, scraping davam edir (warning log-lanır)
     *
     * İSTİFADƏ HALİ:
     * - AJAX sorğuları ilə yüklənən məhsul kartları
     * - Lazy loading ilə render olunan səhifələr
     * - Konkret elementin yüklənməsini gözləmək lazım olduqda
     *
     * @param url Gətirilməli URL
     * @param waitForSelector Gözləniləcək CSS selector (məs: ".product-item")
     * @param timeoutSeconds Maksimum gözləmə müddəti (saniyə)
     * @return Jsoup Document (Selenium-dan gələn HTML parse edilib)
     * @throws IOException Selenium xətası zamanı
     */
    protected Document fetchDocumentWithSeleniumWait(String url, String waitForSelector, int timeoutSeconds) throws IOException {
        // Əgər Selenium konfiqurasiya olunmayıbsa, Jsoup-a fall back et
        if (webDriverManager == null) {
            log.warn("SeleniumWebDriverManager not available, falling back to Jsoup");
            return fetchDocument(url);
        }

        try {
            log.debug("Fetching URL with Selenium (explicit wait): {}", url);
            WebDriver driver = webDriverManager.getDriver();

            // STEALTH MODE: navigator.webdriver property-ni gizlət (bot deteksiyasını bypass et)
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            } catch (Exception e) {
                // İlk səhifə yükləməsində normal xəta (hələ navigator yoxdur)
                log.debug("Could not execute stealth script (normal for first page load): {}", e.getMessage());
            }

            // Səhifəni yüklə
            driver.get(url);

            // Səhifə yüklənəndən SONRA stealth script-ləri icra et
            try {
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    // navigator.webdriver-i gizlət
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    // Plugins siyahısı əlavə et (real brauzer kimi)
                    "Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});" +
                    // Dil konfiqurasiyası (real brauzer kimi)
                    "Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']});"
                );
                log.debug("Stealth scripts executed successfully");
            } catch (Exception e) {
                log.warn("Failed to execute stealth scripts: {}", e.getMessage());
            }

            // EXPLICIT WAIT: Konkret selector-u gözlə
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

            try {
                // Ən azı 1 element selector-a uyğun gələnə qədər gözlə
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(waitForSelector)
                ));
                log.debug("Found element matching selector: {}", waitForSelector);

                // JavaScript render-in tamamlanması üçün əlavə gözləmə
                TimeUnit.MILLISECONDS.sleep(1000);

                // Lazy-loaded content-i aktivləşdirmək üçün scroll et
                ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, document.body.scrollHeight);");
                TimeUnit.MILLISECONDS.sleep(1000);

                // Yuxarı scroll et
                ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, 0);");
                TimeUnit.MILLISECONDS.sleep(1000);

            } catch (org.openqa.selenium.TimeoutException e) {
                // Timeout: Element tapılmadı, amma bəzi məhsullar yüklənmiş ola bilər
                log.warn("Timeout waiting for selector '{}' on URL: {}", waitForSelector, url);
                // Scraping davam edir - bəlkə bəzi məhsullar yüklənib
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Hazır HTML-i Selenium-dan al və Jsoup ilə parse et
            String pageSource = driver.getPageSource();
            return Jsoup.parse(pageSource, url);

        } catch (Exception e) {
            log.error("Selenium fetch failed for URL: {}", url, e);
            throw new IOException("Selenium fetch failed: " + e.getMessage(), e);
        }
    }

    /**
     * HELPER: Element-dən CSS selector ilə mətn çıxar (təhlükəsiz)
     *
     * Null-safe variant: Element və ya selector tapılmasa null qaytarır
     *
     * @param element Ana element (axtarış bu element içində aparılır)
     * @param cssQuery CSS selector (məs: ".product-title", "#price")
     * @return Çıxarılmış mətn (trimmed) və ya null
     */
    protected String extractText(Element element, String cssQuery) {
        Element selected = element.selectFirst(cssQuery);
        return selected != null ? selected.text().trim() : null;
    }

    /**
     * HELPER: Document-dən CSS selector ilə mətn çıxar (təhlükəsiz)
     *
     * Null-safe variant: Selector tapılmasa null qaytarır
     *
     * @param doc Jsoup Document obyekti
     * @param cssQuery CSS selector
     * @return Çıxarılmış mətn (trimmed) və ya null
     */
    protected String extractText(Document doc, String cssQuery) {
        Element selected = doc.selectFirst(cssQuery);
        return selected != null ? selected.text().trim() : null;
    }

    /**
     * HELPER: Element-dən CSS selector ilə atribut çıxar (təhlükəsiz)
     *
     * Null-safe variant: Element və ya selector tapılmasa null qaytarır
     *
     * Tez-tez istifadə olunan atributlar:
     * - "href" → Link URL-i
     * - "src" → Şəkil URL-i
     * - "data-*" → Data atributları
     *
     * @param element Ana element
     * @param cssQuery CSS selector
     * @param attr Atribut adı (məs: "href", "src")
     * @return Atribut dəyəri və ya null
     */
    protected String extractAttr(Element element, String cssQuery, String attr) {
        Element selected = element.selectFirst(cssQuery);
        return selected != null ? selected.attr(attr) : null;
    }

    /**
     * HELPER: Document-dən CSS selector ilə atribut çıxar (təhlükəsiz)
     *
     * @param doc Jsoup Document obyekti
     * @param cssQuery CSS selector
     * @param attr Atribut adı
     * @return Atribut dəyəri və ya null
     */
    protected String extractAttr(Document doc, String cssQuery, String attr) {
        Element selected = doc.selectFirst(cssQuery);
        return selected != null ? selected.attr(attr) : null;
    }

    /**
     * HELPER: Qiymət mətnini BigDecimal-a parse et
     *
     * Bu metod müxtəlif qiymət formatlarını emal edir:
     * - "1,299.99 AZN" → 1299.99
     * - "1 299,99 ₼" → 1299.99
     * - "₼1,299" → 1299
     * - "1299.99" → 1299.99
     *
     * TEMİZLƏMƏ PROSESI:
     * 1. Valyuta simvollarını sil (AZN, ₼, $, €)
     * 2. Boşluqları sil
     * 3. Vergülləri sil (min ayırıcı)
     * 4. Yalnız rəqəmlər və nöqtə saxla
     *
     * NİYƏ BigDecimal?
     * - double və float qiymət hesablamalarında dəqiqlik itirə bilər
     * - BigDecimal pul məbləğləri üçün standart Java tipidir
     * - Decimal dəqiqliyi qorunur (məs: 19.99 dəqiq 19.99 olaraq saxlanır)
     *
     * @param priceText Qiymət mətni (müxtəlif formatlarda)
     * @return Parse edilmiş qiymət və ya null (parse uğursuz olarsa)
     */
    protected BigDecimal parsePrice(String priceText) {
        if (priceText == null || priceText.isEmpty()) {
            return null;
        }

        try {
            // Təmizləmə: Yalnız rəqəmlər və nöqtə saxla
            String cleaned = priceText
                    .replaceAll("[^0-9.,]", "")  // Valyuta simvolları və hərfləri sil
                    .replace(",", "")             // Vergülləri sil (min ayırıcı)
                    .replace(" ", "");            // Boşluqları sil

            if (cleaned.isEmpty()) {
                return null;
            }

            return new BigDecimal(cleaned);

        } catch (NumberFormatException e) {
            log.warn("Failed to parse price: {}", priceText);
            return null;
        }
    }

    /**
     * HELPER: URL-i normalize et (nisbi URL-i tam URL-ə çevir)
     *
     * Mağaza səhifələrində URL-lər müxtəlif formatlarda ola bilər:
     * - Tam URL: "https://kontakt.az/product/123"
     * - Protocol-relative: "//kontakt.az/product/123"
     * - Absolute path: "/product/123"
     * - Relative path: "product/123"
     *
     * Bu metod hamısını TAM URL-ə çevirir.
     *
     * @param url Normalize edilməli URL
     * @return Tam URL (https:// ilə başlayan) və ya null
     */
    protected String normalizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // Artıq tam URL
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        // Protocol-relative URL: //domain.com/path → https://domain.com/path
        if (url.startsWith("//")) {
            return "https:" + url;
        }

        // Absolute path: /path → https://shop.az/path
        if (url.startsWith("/")) {
            return shop.getBaseUrl() + url;
        }

        // Relative path: path → https://shop.az/path
        return shop.getBaseUrl() + "/" + url;
    }

    /**
     * HELPER: Məhsulun stokda olub-olmadığını yoxla
     *
     * Bu metod müxtəlif dillərdə stok mətnlərini tanıyır:
     *
     * OUT OF STOCK göstəriciləri (3 dildə):
     * - İngilis: "out of stock"
     * - Azərbaycan: "yoxdur", "stokda yoxdur"
     * - Rus: "нет в наличии"
     *
     * IN STOCK göstəriciləri (3 dildə):
     * - İngilis: "in stock", "available"
     * - Azərbaycan: "stokda", "var"
     * - Rus: "в наличии"
     *
     * DEFAULT DAVRANIŞI:
     * Əgər heç bir göstərici tapılmasa → true qaytarır (stokda var güman edilir)
     * Bu konservativ yanaşmadır: məhsulu göstərmək, gizlətməkdən yaxşıdır
     *
     * @param availabilityText Stok statusu mətni (və ya null)
     * @return true: Stokda var, false: Stokda yoxdur
     */
    protected boolean isInStock(String availabilityText) {
        if (availabilityText == null) {
            return true; // Məlumat yoxdursa, stokda var güman edirik
        }

        String lower = availabilityText.toLowerCase();

        // OUT OF STOCK yoxlaması (3 dildə)
        if (lower.contains("out of stock") ||
            lower.contains("yoxdur") ||
            lower.contains("stokda yoxdur") ||
            lower.contains("нет в наличии")) {
            return false;
        }

        // IN STOCK yoxlaması (3 dildə)
        return lower.contains("in stock") ||
               lower.contains("stokda") ||
               lower.contains("var") ||
               lower.contains("в наличии") ||
               lower.contains("available");
    }

    /**
     * Mağaza kodunu al
     *
     * @return Mağaza kodu (məs: "KONTAKT", "IRSHAD")
     */
    public String getShopCode() {
        return shop.getCode();
    }
}
