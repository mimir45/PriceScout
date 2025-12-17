package com.samir.pricecomparator.service.scraper.impl;

import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.service.scraper.AbstractShopScraper;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.JavascriptExecutor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class IrshadScraper extends AbstractShopScraper {

    private static final String CATEGORY_URL = "https://irshad.az/az/telefon-ve-aksesuarlar/mobil-telefonlar";
    private static final int MAX_LOAD_MORE_CLICKS = 10;

    private static final String PRODUCT_ITEM_SELECTOR = ".product";
    private static final String LOAD_MORE_BUTTON_SELECTOR = "#loadMore";

    public IrshadScraper(Shop shop) {
        super(shop);
    }

    @Override
    protected List<String> extractProductUrls() throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<ScrapedProductDto> scrape() {
        log.info("Starting scrape for shop: {}", shop.getCode());
        List<ScrapedProductDto> products = new ArrayList<>();

        if (webDriverManager == null) {
            log.error("SeleniumWebDriverManager is required but not available");
            return products;
        }

        WebDriver driver = null;
        try {
            driver = webDriverManager.getDriver();
            driver.get(CATEGORY_URL);

            TimeUnit.SECONDS.sleep(4);

            int clickCount = 0;
            while (clickCount < MAX_LOAD_MORE_CLICKS) {
                try {
                    List<WebElement> loadMoreButtons = driver.findElements(
                        By.cssSelector(LOAD_MORE_BUTTON_SELECTOR)
                    );

                    if (loadMoreButtons.isEmpty() || !loadMoreButtons.get(0).isDisplayed()) {
                        log.info("Load More button not found or not visible. All products loaded.");
                        break;
                    }

                    WebElement loadMoreBtn = loadMoreButtons.get(0);

                    ((JavascriptExecutor) driver).executeScript(
                        "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                        loadMoreBtn
                    );

                    TimeUnit.SECONDS.sleep(1);

                    try {
                        loadMoreBtn.click();
                    } catch (Exception e) {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", loadMoreBtn);
                    }

                    clickCount++;
                    log.info("Clicked 'Load More' button {} times", clickCount);

                    TimeUnit.SECONDS.sleep(3);

                } catch (Exception e) {
                    log.warn("Could not click 'Load More' button: {}", e.getMessage());
                    break;
                }
            }

            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource, CATEGORY_URL);

            Elements productItems = doc.select(PRODUCT_ITEM_SELECTOR);
            log.info("Found {} total products after loading", productItems.size());

            for (Element item : productItems) {
                try {
                    Optional<ScrapedProductDto> product = scrapeProductFromListingItem(item);
                    product.ifPresent(products::add);

                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (Exception e) {
                    log.error("Failed to scrape product item", e);
                }
            }

        } catch (Exception e) {
            log.error("Failed to scrape shop: {}", shop.getCode(), e);
        }

        log.info("Scraped {} products from {}", products.size(), shop.getCode());
        return products;
    }

    private Optional<ScrapedProductDto> scrapeProductFromListingItem(Element item) {
        try {
            Element linkElement = item.selectFirst("a[href*='/az/mehsullar/']");
            if (linkElement == null) {
                log.warn("No product link found in product container");
                return Optional.empty();
            }

            String url = linkElement.attr("href");
            url = normalizeUrl(url);

            String title = linkElement.text().trim();
            if (title == null || title.isEmpty()) {
                Element titleEl = item.selectFirst(".product__title, .product-title, h3, h4");
                if (titleEl != null) {
                    title = titleEl.text().trim();
                }
            }

            BigDecimal price = null;
            BigDecimal oldPrice = null;

            Element priceContainer = item.selectFirst(".product__price__current");
            if (priceContainer != null) {
                String priceText = priceContainer.text();

                String[] prices = priceText.split("AZN");

                if (prices.length >= 2) {
                    oldPrice = parsePrice(prices[0].trim() + " AZN");
                    price = parsePrice(prices[1].trim() + " AZN");
                } else if (prices.length == 1) {
                    price = parsePrice(prices[0].trim() + " AZN");
                }
            }

            if (price == null) {
                Element priceEl = item.selectFirst(".price, [class*='price']");
                if (priceEl != null) {
                    price = parsePrice(priceEl.text());
                }
            }

            String imageUrl = extractImageUrlFromElement(item);

            String color = extractColorFromTitle(title);

            if (title == null || title.isEmpty()) {
                log.warn("No title found for product");
                return Optional.empty();
            }

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No valid price found for product: {}", title);
            }

            boolean inStock = true;

            ScrapedProductDto product = ScrapedProductDto.builder()
                    .shopCode(shop.getCode())
                    .title(title)
                    .url(url)
                    .price(price)
                    .oldPrice(oldPrice)
                    .currency("AZN")
                    .condition("NEW")
                    .color(color)
                    .imageUrl(imageUrl)
                    .inStock(inStock)
                    .build();

            return Optional.of(product);

        } catch (Exception e) {
            log.error("Failed to parse product item", e);
            return Optional.empty();
        }
    }

    @Override
    protected Optional<ScrapedProductDto> scrapeProduct(String url) {
        // NOT USED - we scrape from category page instead
        return Optional.empty();
    }


    /**
     * Extract product image URL from Element
     */
    private String extractImageUrlFromElement(Element element) {
        // Try to get product image
        String[] selectors = {
            "img[src*='storage.irshad.az/products']",
            "img[data-src*='storage.irshad.az']",
            "img[src]",
            "img[data-src]"
        };

        for (String selector : selectors) {
            // First try data-src (lazy loading)
            String imageUrl = extractAttr(element, selector, "data-src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return normalizeUrl(imageUrl);
            }
            // Then try regular src
            imageUrl = extractAttr(element, selector, "src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return normalizeUrl(imageUrl);
            }
        }

        return null;
    }

    /**
     * Extract color from title using pattern matching
     * Supports English, Azerbaijani, and Russian
     */
    private String extractColorFromTitle(String title) {
        if (title == null) {
            return null;
        }

        String lowerTitle = title.toLowerCase();

        // English colors
        if (lowerTitle.contains("black")) return "Black";
        if (lowerTitle.contains("white")) return "White";
        if (lowerTitle.contains("blue")) return "Blue";
        if (lowerTitle.contains("red")) return "Red";
        if (lowerTitle.contains("green")) return "Green";
        if (lowerTitle.contains("gold")) return "Gold";
        if (lowerTitle.contains("silver")) return "Silver";
        if (lowerTitle.contains("gray") || lowerTitle.contains("grey")) return "Gray";
        if (lowerTitle.contains("pink")) return "Pink";
        if (lowerTitle.contains("purple")) return "Purple";
        if (lowerTitle.contains("yellow")) return "Yellow";
        if (lowerTitle.contains("orange")) return "Orange";
        if (lowerTitle.contains("teal")) return "Teal";
        if (lowerTitle.contains("midnight")) return "Midnight";
        if (lowerTitle.contains("starlight")) return "Starlight";
        if (lowerTitle.contains("titanium")) return "Titanium";

        // Azerbaijani colors
        if (lowerTitle.contains("qara")) return "Black";
        if (lowerTitle.contains("ağ")) return "White";
        if (lowerTitle.contains("mavi")) return "Blue";
        if (lowerTitle.contains("qırmızı")) return "Red";
        if (lowerTitle.contains("yaşıl")) return "Green";
        if (lowerTitle.contains("qızılı")) return "Gold";
        if (lowerTitle.contains("gümüşü")) return "Silver";
        if (lowerTitle.contains("boz")) return "Gray";
        if (lowerTitle.contains("çəhrayı")) return "Pink";

        // Russian colors
        if (lowerTitle.contains("черный") || lowerTitle.contains("чёрный")) return "Black";
        if (lowerTitle.contains("белый")) return "White";
        if (lowerTitle.contains("синий") || lowerTitle.contains("голубой")) return "Blue";
        if (lowerTitle.contains("красный")) return "Red";
        if (lowerTitle.contains("зелёный") || lowerTitle.contains("зеленый")) return "Green";
        if (lowerTitle.contains("золотой")) return "Gold";
        if (lowerTitle.contains("серебряный") || lowerTitle.contains("серебристый")) return "Silver";
        if (lowerTitle.contains("серый")) return "Gray";
        if (lowerTitle.contains("розовый")) return "Pink";

        return null;
    }

    /**
     * Extract price text from a larger text block containing price
     */
    private String extractPriceFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String[] parts = text.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.matches(".*\\d+.*")) {
                if (i + 1 < parts.length && (parts[i + 1].contains("AZN") || parts[i + 1].contains("₼"))) {
                    return part + " " + parts[i + 1];
                }
                if (part.contains("AZN") || part.contains("₼")) {
                    return part;
                }
            }
        }

        return null;
    }
}
