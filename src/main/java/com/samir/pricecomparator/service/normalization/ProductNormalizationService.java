package com.samir.pricecomparator.service.normalization;

import com.samir.pricecomparator.dto.NormalizedProduct;
import com.samir.pricecomparator.dto.ScrapedProductDto;
import com.samir.pricecomparator.util.BrandModelParser;
import com.samir.pricecomparator.util.ColorExtractor;
import com.samir.pricecomparator.util.TextNormalizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductNormalizationService {

    private final BrandModelParser brandModelParser;
    private final ColorExtractor colorExtractor;

    public List<NormalizedProduct> normalize(List<ScrapedProductDto> scrapedProducts) {
        log.info("Normalizing {} scraped products", scrapedProducts.size());

        return scrapedProducts.stream()
                .map(this::normalizeProduct)
                .toList();
    }

    public NormalizedProduct normalizeProduct(ScrapedProductDto scraped) {
        String normalizedTitle = null;
        BrandModelParser.BrandModelResult brandModel = new BrandModelParser.BrandModelResult(null, null);
        String color = scraped.getColor();

        try {
            normalizedTitle = TextNormalizationUtil.normalize(scraped.getTitle());
            brandModel = brandModelParser.parse(scraped.getTitle());

            if (color == null || color.isEmpty()) {
                color = colorExtractor.extractColor(scraped.getTitle());
            }
        } catch (Exception e) {
            log.error("Failed during normalization of product title '{}': {}", scraped.getTitle(), e.getMessage(), e);
            if (normalizedTitle == null) {
                normalizedTitle = TextNormalizationUtil.normalize(scraped.getTitle());
            }
        }

        NormalizedProduct normalized = NormalizedProduct.builder()
                .shopCode(scraped.getShopCode())
                .rawTitle(scraped.getTitle())
                .normalizedName(normalizedTitle)
                .brand(brandModel.getBrand())
                .model(brandModel.getModel())
                .category("SMARTPHONE") // Fixed category for now
                .url(scraped.getUrl())
                .price(scraped.getPrice())
                .oldPrice(scraped.getOldPrice())
                .currency(scraped.getCurrency())
                .condition(scraped.getCondition())
                .color(color)
                .imageUrl(scraped.getImageUrl())
                .inStock(scraped.isInStock())
                .build();

        log.debug("Normalized product: {} -> Brand: {}, Model: {}, Color: {}",
                scraped.getTitle(), brandModel.getBrand(), brandModel.getModel(), color);

        return normalized;
    }
}
