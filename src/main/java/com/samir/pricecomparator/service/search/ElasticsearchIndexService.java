package com.samir.pricecomparator.service.search;

import com.samir.pricecomparator.entity.OfferDocument;
import com.samir.pricecomparator.entity.ProductOffer;
import com.samir.pricecomparator.repository.OfferElasticsearchRepository;
import com.samir.pricecomparator.repository.ProductOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;


@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexService {

    private final ProductOfferRepository productOfferRepository;
    private final OfferElasticsearchRepository elasticsearchRepository;

    private static final int BATCH_SIZE = 100;

    public IndexStats indexAllOffers() {
        log.info("Starting full Elasticsearch indexing...");
        IndexStats stats = new IndexStats();

        try {
            try {
                elasticsearchRepository.deleteAll();
                log.info("Cleared existing Elasticsearch index");
            } catch (Exception e) {
                log.info("Index doesn't exist yet, will be created on first save: {}", e.getMessage());
            }

            List<ProductOffer> offers = productOfferRepository.findByInStockTrue();
            log.info("Found {} in-stock offers to index", offers.size());

            if (offers.isEmpty()) {
                return stats;
            }

            List<OfferDocument> batch = new ArrayList<>();
            for (ProductOffer offer : offers) {
                try {
                    OfferDocument document = convertToDocument(offer);
                    batch.add(document);

                    if (batch.size() >= BATCH_SIZE) {
                        indexBatch(batch, stats);
                        batch.clear();
                    }
                } catch (Exception e) {
                    log.error("Failed to convert offer to document: {}", offer.getId(), e);
                    stats.incrementFailed();
                }
            }

            if (!batch.isEmpty()) {
                indexBatch(batch, stats);
            }

            log.info("Elasticsearch indexing completed: {} indexed, {} failed",
                    stats.getIndexed(), stats.getFailed());

        } catch (Exception e) {
            log.error("Elasticsearch indexing failed", e);
            stats.setError(e.getMessage());
        }

        return stats;
    }

    private void indexBatch(List<OfferDocument> batch, IndexStats stats) {
        try {
            elasticsearchRepository.saveAll(batch);
            stats.incrementIndexed(batch.size());
            log.debug("Indexed batch of {} documents", batch.size());
        } catch (Exception e) {
            log.error("Failed to index batch", e);
            stats.incrementFailed(batch.size());
        }
    }

    public void indexOffer(ProductOffer offer) {
        try {
            if (!offer.isInStock()) {
                log.debug("Skipping out-of-stock offer: {}", offer.getId());
                return;
            }

            OfferDocument document = convertToDocument(offer);
            elasticsearchRepository.save(document);
            log.debug("Indexed offer: {} - {}", offer.getId(), offer.getProduct().getNormalizedName());
        } catch (Exception e) {
            log.error("Failed to index offer: {}", offer.getId(), e);
        }
    }

    public void removeFromIndex(Long offerId) {
        try {
            elasticsearchRepository.deleteById(offerId);
            log.debug("Removed offer from index: {}", offerId);
        } catch (Exception e) {
            log.error("Failed to remove offer from index: {}", offerId, e);
        }
    }


    private OfferDocument convertToDocument(ProductOffer offer) {
        OfferDocument doc = new OfferDocument();

        doc.setId(offer.getId());
        doc.setProductId(offer.getProduct().getId());
        doc.setShopId(offer.getShop().getId());

        // Set both the raw product name (title from shop) and normalized name
        doc.setProductName(offer.getTitle());
        doc.setNormalizedName(offer.getProduct().getNormalizedName());
        doc.setBrand(offer.getProduct().getBrand());
        doc.setModel(offer.getProduct().getModel());
        doc.setCategory(offer.getProduct().getCategory());

        doc.setPrice(offer.getPrice());
        doc.setOldPrice(offer.getOldPrice());
        doc.setCurrency(offer.getCurrency());
        doc.setCondition(offer.getCondition());
        doc.setColor(offer.getColor());
        doc.setInStock(offer.isInStock());
        doc.setActive(offer.isActive());

        doc.setShopCode(offer.getShop().getCode());
        doc.setShopName(offer.getShop().getName());

        doc.setUrl(offer.getUrl());
        doc.setImageUrl(offer.getImageUrl());

        doc.setFirstSeenAt(offer.getFirstSeenAt() != null ? offer.getFirstSeenAt().toLocalDate() : null);
        doc.setLastSeenAt(offer.getLastSeenAt() != null ? offer.getLastSeenAt().toLocalDate() : null);
        doc.setIndexedAt(java.time.LocalDate.now());

        return doc;
    }

    public boolean isHealthy() {
        try {
            long count = elasticsearchRepository.count();
            log.debug("Elasticsearch health check: {} documents indexed", count);
            return true;
        } catch (Exception e) {
            log.error("Elasticsearch health check failed", e);
            return false;
        }
    }

    public IndexStats getIndexStats() {
        IndexStats stats = new IndexStats();
        try {
            long count = elasticsearchRepository.count();
            stats.incrementIndexed((int) count);
            log.info("Elasticsearch contains {} documents", count);
        } catch (Exception e) {
            log.error("Failed to get index stats", e);
            stats.setError(e.getMessage());
        }
        return stats;
    }

    public static class IndexStats {
        private int indexed = 0;
        private int failed = 0;
        private String error;

        public void incrementIndexed() { indexed++; }
        public void incrementIndexed(int count) { indexed += count; }
        public void incrementFailed() { failed++; }
        public void incrementFailed(int count) { failed += count; }

        public int getIndexed() { return indexed; }
        public int getFailed() { return failed; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}
