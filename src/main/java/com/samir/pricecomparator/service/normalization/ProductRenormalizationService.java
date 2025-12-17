package com.samir.pricecomparator.service.normalization;

import com.samir.pricecomparator.entity.Product;
import com.samir.pricecomparator.entity.ProductOffer;
import com.samir.pricecomparator.repository.ProductOfferRepository;
import com.samir.pricecomparator.repository.ProductRepository;
import com.samir.pricecomparator.util.BrandModelParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductRenormalizationService {

    private final ProductRepository productRepository;
    private final ProductOfferRepository productOfferRepository;
    private final BrandModelParser brandModelParser;

    @Transactional
    public RenormalizationResult renormalizeAllProducts() {
        log.info("Starting renormalization of all products");

        List<Product> products = productRepository.findAll();
        int updated = 0;
        int unchanged = 0;
        int errors = 0;

        for (Product product : products) {
            try {
                List<ProductOffer> offers = productOfferRepository.findByProduct(product);

                if (offers.isEmpty()) {
                    log.warn("Product {} has no offers, skipping", product.getId());
                    unchanged++;
                    continue;
                }

                String title = offers.get(0).getTitle();
                BrandModelParser.BrandModelResult result = brandModelParser.parse(title);

                boolean changed = false;

                if (result.getBrand() != null && !result.getBrand().equals(product.getBrand())) {
                    log.debug("Updating product {} brand: {} -> {}",
                            product.getId(), product.getBrand(), result.getBrand());
                    product.setBrand(result.getBrand());
                    changed = true;
                }

                if (result.getModel() != null && !result.getModel().equals(product.getModel())) {
                    log.debug("Updating product {} model: {} -> {}",
                            product.getId(), product.getModel(), result.getModel());
                    product.setModel(result.getModel());
                    changed = true;
                }

                if (changed) {
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);
                    updated++;
                    log.info("Renormalized product {}: {} - Brand: {}, Model: {}",
                            product.getId(), title, result.getBrand(), result.getModel());
                } else {
                    unchanged++;
                }

            } catch (Exception e) {
                log.error("Failed to renormalize product {}: {}", product.getId(), e.getMessage(), e);
                errors++;
            }
        }

        RenormalizationResult result = new RenormalizationResult(
                products.size(),
                updated,
                unchanged,
                errors
        );

        log.info("Renormalization complete: {} total, {} updated, {} unchanged, {} errors",
                result.total, result.updated, result.unchanged, result.errors);

        return result;
    }

    @Transactional
    public RenormalizationResult renormalizeProductsWithMissingData() {
        log.info("Starting renormalization of products with missing brand/model");

        List<Product> productsWithMissingBrand = productRepository.findByBrandIsNull();
        List<Product> productsWithMissingModel = productRepository.findByModelIsNull();

        List<Product> products = productsWithMissingBrand.stream()
                .distinct()
                .toList();

        List<Product> modelOnlyMissing = productsWithMissingModel.stream()
                .filter(p -> p.getBrand() != null)
                .toList();

        int totalProducts = products.size() + modelOnlyMissing.size();
        int updated = 0;
        int unchanged = 0;
        int errors = 0;

        for (Product product : products) {
            try {
                List<ProductOffer> offers = productOfferRepository.findByProduct(product);

                if (offers.isEmpty()) {
                    unchanged++;
                    continue;
                }

                String title = offers.get(0).getTitle();
                BrandModelParser.BrandModelResult result = brandModelParser.parse(title);

                boolean changed = false;

                if (result.getBrand() != null) {
                    product.setBrand(result.getBrand());
                    changed = true;
                }

                if (result.getModel() != null) {
                    product.setModel(result.getModel());
                    changed = true;
                }

                if (changed) {
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);
                    updated++;
                } else {
                    unchanged++;
                }

            } catch (Exception e) {
                log.error("Failed to renormalize product {}: {}", product.getId(), e.getMessage());
                errors++;
            }
        }

        for (Product product : modelOnlyMissing) {
            try {
                List<ProductOffer> offers = productOfferRepository.findByProduct(product);

                if (offers.isEmpty()) {
                    unchanged++;
                    continue;
                }

                String title = offers.get(0).getTitle();
                BrandModelParser.BrandModelResult result = brandModelParser.parse(title);

                if (result.getModel() != null) {
                    product.setModel(result.getModel());
                    product.setUpdatedAt(LocalDateTime.now());
                    productRepository.save(product);
                    updated++;
                } else {
                    unchanged++;
                }

            } catch (Exception e) {
                log.error("Failed to renormalize product {}: {}", product.getId(), e.getMessage());
                errors++;
            }
        }

        RenormalizationResult renormalizationResult = new RenormalizationResult(
                totalProducts,
                updated,
                unchanged,
                errors
        );

        log.info("Renormalization of missing data complete: {} total, {} updated, {} unchanged, {} errors",
                renormalizationResult.total, renormalizationResult.updated, renormalizationResult.unchanged, renormalizationResult.errors);

        return renormalizationResult;
    }

    public record RenormalizationResult(
            int total,
            int updated,
            int unchanged,
            int errors
    ) {}
}