package com.samir.pricecomparator.service.scraper;

import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.service.scraper.impl.BakuElectronicsScraper;
import com.samir.pricecomparator.service.scraper.impl.IrshadScraper;
import com.samir.pricecomparator.service.scraper.impl.KontaktScraper;
import com.samir.pricecomparator.util.SeleniumWebDriverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShopScraperFactory {

    private final SeleniumWebDriverManager webDriverManager;

    @Autowired
    public ShopScraperFactory(SeleniumWebDriverManager webDriverManager) {
        this.webDriverManager = webDriverManager;
    }

    public AbstractShopScraper getScraper(Shop shop) {
        AbstractShopScraper scraper = switch (shop.getCode().toUpperCase()) {
            case "KONTAKT" -> new KontaktScraper(shop);
            case "IRSHAD" -> new IrshadScraper(shop);
            case "BAKU_ELECTRONICS" -> new BakuElectronicsScraper(shop);
            default -> throw new IllegalArgumentException(
                    "No scraper implementation found for shop: " + shop.getCode()
            );
        };

        scraper.webDriverManager = webDriverManager;
        return scraper;
    }

    public boolean hasScraper(String shopCode) {
        try {
            return switch (shopCode.toUpperCase()) {
                case "KONTAKT" -> true;
                case "IRSHAD" -> true;
                case "BAKU_ELECTRONICS" -> true;
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }
}
