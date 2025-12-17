package com.samir.pricecomparator.service.persistence;

import com.samir.pricecomparator.dto.NormalizedProduct;
import com.samir.pricecomparator.entity.Product;
import com.samir.pricecomparator.entity.ProductOffer;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.repository.ProductOfferRepository;
import com.samir.pricecomparator.repository.ProductRepository;
import com.samir.pricecomparator.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferPersistenceService {

    private final ProductRepository productRepository;
    private final ProductOfferRepository productOfferRepository;
    private final ShopRepository shopRepository;
    private final ProductMatchingService productMatchingService;

    @Transactional
    public PersistenceStats persistOffers(List<NormalizedProduct> normalizedProducts) {
        PersistenceStats stats = new PersistenceStats();

        for (NormalizedProduct normalized : normalizedProducts) {
            try {
                persistSingleOffer(normalized, stats);
            } catch (Exception e) {
                log.error("Failed to persist product: {}", normalized.getRawTitle(), e);
                stats.incrementFailed();
            }
        }

        log.info("Persistence completed: {} created, {} updated, {} failed",
                stats.getCreated(), stats.getUpdated(), stats.getFailed());

        return stats;
    }

    private void persistSingleOffer(NormalizedProduct normalized, PersistenceStats stats) {
        Shop shop = shopRepository.findByCodeIgnoreCase(normalized.getShopCode())
                .orElseThrow(() -> new IllegalStateException("Shop not found: " + normalized.getShopCode()));

        Product product = findOrCreateProduct(normalized);

        Optional<ProductOffer> existingOffer = productOfferRepository
                .findByProductAndShop(product, shop);

        if (existingOffer.isPresent()) {
            updateExistingOffer(existingOffer.get(), normalized, stats);
        } else {
            createNewOffer(product, shop, normalized, stats);
        }
    }

    private Product findOrCreateProduct(NormalizedProduct normalized) {
        Optional<Product> existingProduct = productMatchingService
                .findMatchingProduct(normalized);

        if (existingProduct.isPresent()) {
            Product product = existingProduct.get();
            log.debug("Found existing product: {}", product.getNormalizedName());

            boolean changed = false;
            if (normalized.getBrand() != null && !normalized.getBrand().equals(product.getBrand())) {
                product.setBrand(normalized.getBrand());
                changed = true;
            }
            if (normalized.getModel() != null && !normalized.getModel().equals(product.getModel())) {
                product.setModel(normalized.getModel());
                changed = true;
            }

            if (changed) {
                product.setUpdatedAt(LocalDateTime.now());
                productRepository.save(product);
                log.info("Updated existing product: {} (ID: {}) with new brand/model", product.getNormalizedName(), product.getId());
            }
            return product;
        }

        Product newProduct = new Product();
        newProduct.setNormalizedName(normalized.getNormalizedName());
        newProduct.setBrand(normalized.getBrand());
        newProduct.setModel(normalized.getModel());
        newProduct.setCategory("SMARTPHONE"); // TODO: Add category detection

        LocalDateTime now = LocalDateTime.now();
        newProduct.setCreatedAt(now);
        newProduct.setUpdatedAt(now);

        Product saved = productRepository.save(newProduct);
        log.info("Created new product: {} (ID: {})", saved.getNormalizedName(), saved.getId());
        return saved;
    }

    private void updateExistingOffer(ProductOffer offer, NormalizedProduct normalized, PersistenceStats stats) {
        boolean changed = false;

        BigDecimal newPrice = normalized.getPrice();
        if (newPrice != null && !newPrice.equals(offer.getPrice())) {
            offer.setOldPrice(offer.getPrice()); // Save old price
            offer.setPrice(newPrice);
            changed = true;
        }

        boolean newStock = normalized.isInStock();
        if (newStock != offer.isInStock()) {
            offer.setInStock(newStock);
            changed = true;
        }

        if (!normalized.getUrl().equals(offer.getUrl())) {
            offer.setUrl(normalized.getUrl());
            changed = true;
        }

        if (normalized.getCondition() != null && !normalized.getCondition().equals(offer.getCondition())) {
            offer.setCondition(normalized.getCondition());
            changed = true;
        }

        if (normalized.getColor() != null && !normalized.getColor().equals(offer.getColor())) {
            offer.setColor(normalized.getColor());
            changed = true;
        }

        if (normalized.getImageUrl() != null && !normalized.getImageUrl().equals(offer.getImageUrl())) {
            offer.setImageUrl(normalized.getImageUrl());
            changed = true;
        }

        if (changed) {
            offer.setLastSeenAt(LocalDateTime.now());
            productOfferRepository.save(offer);
            stats.incrementUpdated();
            log.debug("Updated offer: {} at {}", offer.getProduct().getNormalizedName(), offer.getShop().getCode());
        } else {
            offer.setLastSeenAt(LocalDateTime.now());
            productOfferRepository.save(offer);
            log.debug("Offer unchanged, updated lastSeenAt: {}", offer.getId());
        }
    }

    private void createNewOffer(Product product, Shop shop, NormalizedProduct normalized, PersistenceStats stats) {
        ProductOffer offer = new ProductOffer();
        offer.setProduct(product);
        offer.setShop(shop);
        offer.setTitle(normalized.getRawTitle());
        offer.setPrice(normalized.getPrice());
        offer.setOldPrice(normalized.getOldPrice());
        offer.setCurrency(normalized.getCurrency());
        offer.setUrl(normalized.getUrl());
        offer.setCondition(normalized.getCondition());
        offer.setColor(normalized.getColor());
        offer.setImageUrl(normalized.getImageUrl());
        offer.setInStock(normalized.isInStock());
        offer.setActive(true);

        LocalDateTime now = LocalDateTime.now();
        offer.setFirstSeenAt(now);
        offer.setLastSeenAt(now);

        productOfferRepository.save(offer);
        stats.incrementCreated();
        log.info("Created new offer: {} at {} for {} {}",
                product.getNormalizedName(), shop.getCode(), normalized.getPrice(), normalized.getCurrency());
    }

    public static class PersistenceStats {
        private int created = 0;
        private int updated = 0;
        private int failed = 0;

        public void incrementCreated() { created++; }
        public void incrementUpdated() { updated++; }
        public void incrementFailed() { failed++; }

        public int getCreated() { return created; }
        public int getUpdated() { return updated; }
        public int getFailed() { return failed; }
    }
}
