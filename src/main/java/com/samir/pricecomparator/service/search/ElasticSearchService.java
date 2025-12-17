package com.samir.pricecomparator.service.search;

import com.samir.pricecomparator.dto.OfferDto;
import com.samir.pricecomparator.entity.OfferDocument;
import com.samir.pricecomparator.repository.OfferElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class ElasticSearchService {

    private final OfferElasticsearchRepository offerElasticsearchRepository;

    public List<OfferDto> search(
            String query,
            String condition,
            String color,
            List<String> shopCodes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int limit
    ) {
        log.debug("Searching via Elasticsearch: query='{}', condition='{}', color='{}', shops={}, minPrice={}, maxPrice={}, limit={}",
                query, condition, color, shopCodes, minPrice, maxPrice, limit);

        if (limit <= 0) {
            limit = 3;
        }

        try {
            PageRequest pageRequest;
            if (query != null && !query.isBlank()) {
                pageRequest = PageRequest.of(0, limit * 3);
            } else {
                pageRequest = PageRequest.of(0, limit * 3, Sort.by(Sort.Direction.ASC, "price"));
            }

            Page<OfferDocument> page;

            if (query != null && !query.isBlank()) {
                page = offerElasticsearchRepository.fuzzySearchActive(query, pageRequest);
            } else {
                page = offerElasticsearchRepository.findByActiveTrue(pageRequest);
            }

            List<OfferDto> offers = page.getContent().stream()
                    .filter(doc -> filterByCondition(doc, condition))
                    .filter(doc -> filterByColor(doc, color))
                    .filter(doc -> filterByShops(doc, shopCodes))
                    .filter(doc -> filterByPriceRange(doc, minPrice, maxPrice))
                    .map(this::toDto)
                    .limit(limit)
                    .collect(Collectors.toList());

            log.info("Elasticsearch search SUCCESS for query='{}' - found {} results", query, offers.size());
            return offers;

        } catch (Exception e) {
            log.error("Elasticsearch search failed: {}", e.getMessage(), e);
            throw new RuntimeException("Elasticsearch search failed", e);
        }
    }

    private boolean filterByCondition(OfferDocument doc, String condition) {
        return condition == null || condition.isBlank() || condition.equalsIgnoreCase(doc.getCondition());
    }

    private boolean filterByColor(OfferDocument doc, String color) {
        return color == null || color.isBlank() ||
               (doc.getColor() != null && doc.getColor().equalsIgnoreCase(color));
    }

    private boolean filterByShops(OfferDocument doc, List<String> shopCodes) {
        return shopCodes == null || shopCodes.isEmpty() || shopCodes.contains(doc.getShopCode());
    }

    private boolean filterByPriceRange(OfferDocument doc, BigDecimal minPrice, BigDecimal maxPrice) {
        if (doc.getPrice() == null) return false;
        if (minPrice != null && doc.getPrice().compareTo(minPrice) < 0) return false;
        if (maxPrice != null && doc.getPrice().compareTo(maxPrice) > 0) return false;
        return true;
    }

    private OfferDto toDto(OfferDocument doc) {
        return new OfferDto(
                doc.getId(),
                doc.getShopCode(),
                doc.getShopName(),
                doc.getProductName(),
                doc.getNormalizedName(),
                doc.getBrand(),
                doc.getModel(),
                doc.getCategory(),
                doc.getColor(),
                doc.getCondition(),
                doc.getPrice(),
                doc.getOldPrice(),
                doc.getCurrency(),
                doc.getUrl(),
                doc.getImageUrl(),
                doc.isInStock()
        );
    }
}
