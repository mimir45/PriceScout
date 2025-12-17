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
public class BakuElectronicsScraper extends AbstractShopScraper {

    private static final String CATEGORY_URL =
        "https://www.bakuelectronics.az/catalog/telefonlar-qadcetler/smartfonlar-mobil-telefonlar";
    private static final int MAX_PAGES = 30;
    private static final String NEXT_DATA_SCRIPT_SELECTOR = "script#__NEXT_DATA__";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public BakuElectronicsScraper(Shop shop) {
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

        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                try {
                    String pageUrl = buildPageUrl(page);
                    log.info("Fetching page {} from URL: {}", page, pageUrl);

                    Document doc = fetchDocumentWithSelenium(pageUrl);

                    List<ScrapedProductDto> pageProducts = extractProductsFromPage(doc);

                    if (pageProducts.isEmpty()) {
                        log.info("No products found on page {}. Stopping pagination.", page);
                        break;
                    }

                    log.info("Found {} products on page {}", pageProducts.size(), page);
                    products.addAll(pageProducts);

                    for (ScrapedProductDto product : pageProducts) {
                        TimeUnit.MILLISECONDS.sleep(100);
                    }

                    if (page < MAX_PAGES) {
                        TimeUnit.MILLISECONDS.sleep(POLITE_DELAY_MS);
                    }

                } catch (IOException e) {
                    log.error("Failed to fetch page {}: {}", page, e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    log.error("Scraping interrupted: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to scrape shop: {}", shop.getCode(), e);
        }

        log.info("Scraped {} products from {}", products.size(), shop.getCode());
        return products;
    }

    @Override
    protected Optional<ScrapedProductDto> scrapeProduct(String url) {
        return Optional.empty();
    }

    private String buildPageUrl(int page) {
        if (page == 1) {
            return CATEGORY_URL;
        }
        return CATEGORY_URL + "?page=" + page;
    }

    private List<ScrapedProductDto> extractProductsFromPage(Document doc) {
        List<ScrapedProductDto> products = new ArrayList<>();

        Element scriptElement = doc.selectFirst(NEXT_DATA_SCRIPT_SELECTOR);

        if (scriptElement != null) {
            String jsonContent = scriptElement.html();

            if (jsonContent != null && !jsonContent.isEmpty()) {
                try {
                    products = extractProductsFromJson(jsonContent);
                    log.info("Successfully extracted {} products using JSON method", products.size());
                    return products;
                } catch (Exception e) {
                    log.warn("Failed to extract from JSON, falling back to CSS selectors: {}", e.getMessage());
                }
            }
        }

        log.info("Using CSS selector fallback for product extraction");
        products = extractProductsFromHtml(doc);
        log.info("Extracted {} products using CSS method", products.size());

        return products;
    }

    private List<ScrapedProductDto> extractProductsFromJson(String jsonContent) throws Exception {
        List<ScrapedProductDto> products = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(jsonContent);

            // Navigate: props → pageProps → products → products → items
            JsonNode dataNode = root.path("props")
                                    .path("pageProps")
                                    .path("products")
                                    .path("products")
                                    .path("items");

            if (dataNode.isArray()) {
                for (JsonNode productNode : dataNode) {
                    try {
                        Optional<ScrapedProductDto> product = mapJsonToDto(productNode);
                        product.ifPresent(products::add);
                    } catch (Exception e) {
                        log.error("Failed to map product JSON to DTO: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("Expected array at props.pageProps.products.products.items, got: {}", dataNode.getNodeType());
            }

        } catch (Exception e) {
            log.error("Failed to parse __NEXT_DATA__ JSON", e);
            throw e;
        }

        return products;
    }
    private Optional<ScrapedProductDto> mapJsonToDto(JsonNode node) {
        try {
            String name = node.path("name").asText();
            String slug = node.path("slug").asText();
            double discountedPrice = node.path("discounted_price").asDouble();
            double originalPrice = node.path("price").asDouble();
            String discount = node.path("discount").asText();
            int quantity = node.path("quantity").asInt();
            String image = node.path("image").asText();

            if (name == null || name.isEmpty()) {
                log.warn("No title found in JSON product node");
                return Optional.empty();
            }

            if (discountedPrice <= 0) {
                log.warn("Invalid price for product: {}", name);
                return Optional.empty();
            }

            String url = shop.getBaseUrl() + "/mehsul/" + slug;

            BigDecimal price = BigDecimal.valueOf(discountedPrice);
            BigDecimal oldPrice = null;

            try {
                double discountValue = Double.parseDouble(discount);
                if (discountValue > 0) {
                    oldPrice = BigDecimal.valueOf(originalPrice);
                }
            } catch (NumberFormatException e) {
            }

            boolean inStock = quantity > 0;

            String color = extractColorFromTitle(name);

            ScrapedProductDto product = ScrapedProductDto.builder()
                    .shopCode(shop.getCode())
                    .title(name)
                    .url(normalizeUrl(url))
                    .price(price)
                    .oldPrice(oldPrice)
                    .currency("AZN")
                    .condition("NEW")
                    .color(color)
                    .imageUrl(normalizeUrl(image))
                    .inStock(inStock)
                    .build();

            return Optional.of(product);

        } catch (Exception e) {
            log.error("Failed to map JSON node to DTO", e);
            return Optional.empty();
        }
    }

    private List<ScrapedProductDto> extractProductsFromHtml(Document doc) {
        List<ScrapedProductDto> products = new ArrayList<>();

        Elements productLinks = doc.select("a[href^=/mehsul/]");

        for (Element link : productLinks) {
            try {
                Optional<ScrapedProductDto> product = scrapeProductFromHtmlElement(link);
                product.ifPresent(products::add);
            } catch (Exception e) {
                log.error("Failed to scrape product from HTML element", e);
            }
        }

        return products;
    }

    private Optional<ScrapedProductDto> scrapeProductFromHtmlElement(Element element) {
        try {
            String href = element.attr("href");
            String url = normalizeUrl(href);

            String title = null;
            Element titleElement = element.selectFirst("h4");
            if (titleElement != null) {
                title = titleElement.text().trim();
            }

            if (title == null || title.isEmpty()) {
                title = extractText(element, "h4, h3, .product-title");
            }

            if (title == null || title.isEmpty()) {
                log.warn("No title found for product link: {}", url);
                return Optional.empty();
            }

            BigDecimal price = null;
            String priceText = element.text();

            if (priceText.contains("₼")) {
                String[] parts = priceText.split("₼");
                for (String part : parts) {
                    BigDecimal parsedPrice = parsePrice(part.trim() + " ₼");
                    if (parsedPrice != null && parsedPrice.compareTo(BigDecimal.ZERO) > 0) {
                        price = parsedPrice;
                        break;
                    }
                }
            }

            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No valid price found for product: {}", title);
            }

            String imageUrl = extractImageUrlFromElement(element);

            String color = extractColorFromTitle(title);

            boolean inStock = true;

            ScrapedProductDto product = ScrapedProductDto.builder()
                    .shopCode(shop.getCode())
                    .title(title)
                    .url(url)
                    .price(price)
                    .oldPrice(null) // Not available in HTML fallback
                    .currency("AZN")
                    .condition("NEW")
                    .color(color)
                    .imageUrl(imageUrl)
                    .inStock(inStock)
                    .build();

            return Optional.of(product);

        } catch (Exception e) {
            log.error("Failed to parse HTML product element", e);
            return Optional.empty();
        }
    }
    private String extractImageUrlFromElement(Element element) {
        String[] selectors = {
            "img[src*='bakuelectronics.az']",
            "img[data-src*='bakuelectronics.az']",
            "img[src]",
            "img[data-src]"
        };

        for (String selector : selectors) {
            String imageUrl = extractAttr(element, selector, "data-src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return normalizeUrl(imageUrl);
            }
            imageUrl = extractAttr(element, selector, "src");
            if (imageUrl != null && !imageUrl.isEmpty() && !imageUrl.contains("placeholder")) {
                return normalizeUrl(imageUrl);
            }
        }

        return null;
    }

    private String extractColorFromTitle(String title) {
        if (title == null) {
            return null;
        }

        String lowerTitle = title.toLowerCase();

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
}
