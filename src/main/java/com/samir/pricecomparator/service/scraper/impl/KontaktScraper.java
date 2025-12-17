package com.samir.pricecomparator.service.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.service.scraper.AbstractShopScraper;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Slf4j
public class KontaktScraper extends AbstractShopScraper {

    private static final String CATEGORY_URL = "https://kontakt.az/telefoniya/smartfonlar";
    private static final int MAX_PAGES = 15;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public KontaktScraper(Shop shop) {
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

        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                try {
                    String pageUrl = buildPageUrl(page);

                    if (webDriverManager != null) {
                        webDriverManager.closeDriver();
                        log.debug("Closed WebDriver before fetching page {}", page);
                    }

                    Document doc = fetchDocumentWithSeleniumWait(
                        pageUrl,
                        ".prodItem.product-item[data-gtm]",
                        15
                    );

                    Elements productItems = doc.select(".prodItem.product-item[data-gtm]");

                    if (productItems.isEmpty()) {
                        log.warn("No products found on page {} at URL: {}", page, pageUrl);
                        log.debug("Page HTML preview (first 1000 chars): {}",
                            doc.html().substring(0, Math.min(1000, doc.html().length())));

                        Elements paginationLinks = doc.select("a.next-page, a[href*='?p='], .pagination a, a[rel='next']");
                        log.debug("Pagination links found: {}", paginationLinks.size());
                        paginationLinks.forEach(link ->
                            log.debug("Pagination link: text='{}' href='{}'", link.text(), link.attr("href"))
                        );

                        log.debug("Product containers (.contentos): {}", doc.select(".contentos").size());
                        log.debug("Product items (.prodItem): {}", doc.select(".prodItem").size());
                        log.debug("All product-item elements: {}", doc.select(".product-item").size());

                        log.info("Stopping pagination - no products found");
                        break;
                    }

                    log.info("Found {} products on page {} (URL: {})", productItems.size(), page, pageUrl);

                    if (page == 1) {
                        Elements paginationLinks = doc.select("a.next-page, a[href*='?p='], .pagination a, a[rel='next']");
                        log.debug("Page 1 pagination links found: {}", paginationLinks.size());
                        paginationLinks.forEach(link ->
                            log.debug("Page 1 pagination link: text='{}' href='{}'", link.text(), link.attr("href"))
                        );
                    }

                    for (Element item : productItems) {
                        try {
                            Optional<ScrapedProductDto> product = scrapeProductFromListingItem(item);
                            product.ifPresent(products::add);

                            TimeUnit.MILLISECONDS.sleep(100);
                        } catch (Exception e) {
                            log.error("Failed to scrape product item", e);
                        }
                    }

                    int randomDelay = 1000 + new java.util.Random().nextInt(2000);
                    TimeUnit.MILLISECONDS.sleep(randomDelay);
                    log.debug("Waiting {} ms before next page", randomDelay);

                } catch (IOException e) {
                    log.error("Failed to fetch page {}: {}", page, e.getMessage());
                    break;
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
            String title = null;
            BigDecimal price = null;
            BigDecimal oldPrice = null;
            String brand = null;

            String dataGtm = item.attr("data-gtm");
            if (dataGtm != null && !dataGtm.isEmpty()) {
                try {
                    JsonNode gtmData = objectMapper.readTree(dataGtm);

                    title = gtmData.path("item_name").asText();

                    double priceValue = gtmData.path("price").asDouble();
                    if (priceValue > 0) {
                        price = BigDecimal.valueOf(priceValue);
                    }

                    double discount = gtmData.path("discount").asDouble();
                    if (discount > 0) {
                        oldPrice = price.add(BigDecimal.valueOf(discount));
                    }

                    brand = gtmData.path("item_brand").asText();

                    log.debug("Extracted from data-gtm: {}", title);
                } catch (Exception e) {
                    log.warn("Failed to parse data-gtm JSON, falling back to CSS selectors", e);
                }
            }

            if (title == null || title.isEmpty()) {
                title = extractText(item, ".prodTitle");
                if (title == null || title.isEmpty()) {
                    title = extractText(item, "a.prodTitle");
                }
                if (title == null || title.isEmpty()) {
                    title = extractText(item, ".prodCartContent .prodTitle");
                }
                log.debug("Extracted title from CSS: {}", title);
            }

            if (price == null) {
                String priceText = extractText(item, ".product-price-label strong span");
                if (priceText == null) {
                    priceText = extractText(item, ".price");
                }
                if (priceText != null) {
                    price = parsePrice(priceText);
                    log.debug("Extracted price from CSS: {}", price);
                }
            }

            if (title == null || title.isEmpty()) {
                log.warn("No title found (tried both data-gtm and CSS)");
                return Optional.empty();
            }

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid price for: {}", title);
                return Optional.empty();
            }

            String url = null;
            String[] urlSelectors = {
                "a.prodTitle",                      // Product title link
                "a.product-item-link",              // Product link class
                "a[href*='/']",                     // Any link with actual path (not #)
                ".prodCartContent a[href]",         // Link in product content
                "a[href]"                           // Last resort
            };

            for (String selector : urlSelectors) {
                Element link = item.selectFirst(selector);
                if (link != null) {
                    String href = link.attr("href");
                    log.debug("Trying selector '{}': found href='{}'", selector, href);
                    // Skip hash links and invalid URLs
                    if (href != null && !href.isEmpty() && !href.equals("#") && !href.startsWith("javascript:")) {
                        url = normalizeUrl(href);
                        log.debug("Selected URL from selector '{}': {}", selector, url);
                        break;
                    }
                }
            }

            if (url == null || url.contains("#")) {
                log.warn("No valid URL found for product: {}", title);
                return Optional.empty();
            }

            String imageUrl = extractImageUrlFromElement(item);

            String color = extractColorFromTitle(title);

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
                    .imageUrl(normalizeUrl(imageUrl))
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
        return Optional.empty();
    }

    private String buildPageUrl(int page) {
        if (page == 1) {
            return CATEGORY_URL;
        }
        return CATEGORY_URL + "?p=" + page; // Fixed: kontakt.az uses ?p= not ?page=
    }

    private boolean isValidProductUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return !url.contains("/category/") &&
               !url.contains("/search") &&
               !url.contains("/cart") &&
               !url.contains("/checkout");
    }

    private String extractTitle(Document doc) {
        String[] selectors = {
            ".page-title-wrapper.product h1",
            ".page-title-wrapper h1",
            ".prodCart__top h1",
            ".prodCart h1",
            "h1.product-title",
            "h1.product-name",
            ".product-detail h1",
            ".product-info h1",
            "h1[itemprop='name']",
            ".page-title h1",
            "h1"  // Last resort
        };

        for (String selector : selectors) {
            String title = extractText(doc, selector);
            if (title != null && !title.isEmpty()) {
                return title;
            }
        }

        return null;
    }

    private BigDecimal extractPrice(Document doc) {
        // Primary selector: product-price-label strong span contains the current price
        String[] selectors = {
            ".product-price-label strong span",    // Price is in: .product-price-label > strong > span
            ".product-price-label strong",         // Fallback to strong tag
            ".price-box.price-final_price",        // Alternative price container
            ".product-price-label span",
            ".product-price-label",
            "span[itemprop='price']"
        };

        for (String selector : selectors) {
            String priceText = extractText(doc, selector);
            log.debug("Price selector '{}' extracted text: '{}'", selector, priceText);
            if (priceText != null) {
                BigDecimal price = parsePrice(priceText);
                if (price != null) {
                    log.debug("Successfully parsed price: {}", price);
                    return price;
                } else {
                    log.debug("Failed to parse price from text: '{}'", priceText);
                }
            }
        }

        log.warn("No price found with any selector");
        return null;
    }

    private BigDecimal extractOldPrice(Document doc) {
        String[] selectors = {
            ".old-price .price",
            ".price-old",
            ".price-original",
            ".regular-price .price",
            "span.old-price"
        };

        for (String selector : selectors) {
            String oldPriceText = extractText(doc, selector);
            if (oldPriceText != null) {
                BigDecimal price = parsePrice(oldPriceText);
                if (price != null) {
                    return price;
                }
            }
        }

        return null;
    }

    private String extractImageUrl(Document doc) {
        String[] selectors = {
            ".prodCart img",
            ".product-media img",
            ".product-image img",
            ".product-gallery img",
            ".gallery-placeholder img",
            "img[itemprop='image']",
            ".main-image img",
            ".prodItem img"
        };

        for (String selector : selectors) {
            String imageUrl = extractAttr(doc, selector, "data-src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return imageUrl;
            }
            imageUrl = extractAttr(doc, selector, "src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return imageUrl;
            }
        }

        return null;
    }

    private String extractImageUrlFromElement(Element element) {
        String[] selectors = {
            ".prodItem img",                       // Image in product item
            "img[data-src]",                       // Lazy loaded image
            "img[src]"                             // Regular image
        };

        for (String selector : selectors) {
            String imageUrl = extractAttr(element, selector, "data-src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return imageUrl;
            }
            imageUrl = extractAttr(element, selector, "src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return imageUrl;
            }
        }

        return null;
    }

    private String extractColorFromTitle(String title) {
        if (title == null) {
            return null;
        }

        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("black") || lowerTitle.contains("qara")) return "Black";
        if (lowerTitle.contains("white") || lowerTitle.contains("ağ")) return "White";
        if (lowerTitle.contains("blue") || lowerTitle.contains("mavi")) return "Blue";
        if (lowerTitle.contains("red") || lowerTitle.contains("qırmızı")) return "Red";
        if (lowerTitle.contains("green") || lowerTitle.contains("yaşıl")) return "Green";
        if (lowerTitle.contains("gold") || lowerTitle.contains("qızılı")) return "Gold";
        if (lowerTitle.contains("silver") || lowerTitle.contains("gümüşü")) return "Silver";
        if (lowerTitle.contains("teal")) return "Teal";
        if (lowerTitle.contains("pink")) return "Pink";
        if (lowerTitle.contains("midnight")) return "Midnight";
        if (lowerTitle.contains("starlight")) return "Starlight";
        if (lowerTitle.contains("titanium")) return "Titanium";

        return null;
    }

    private String extractAvailability(Document doc) {
        String[] selectors = {
            ".stock .stock-status",
            ".availability",
            ".stock-status",
            ".product-stock",
            "[data-stock-status]",
            ".in-stock",
            ".out-of-stock"
        };

        for (String selector : selectors) {
            String availability = extractText(doc, selector);
            if (availability != null && !availability.isEmpty()) {
                return availability;
            }
        }

        return null;
    }
}
