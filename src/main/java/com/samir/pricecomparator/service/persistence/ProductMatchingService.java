package com.samir.pricecomparator.service.persistence;

import com.samir.pricecomparator.dto.NormalizedProduct;
import com.samir.pricecomparator.entity.Product;
import com.samir.pricecomparator.repository.ProductRepository;
import com.samir.pricecomparator.util.TextNormalizationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProductMatchingService {

    private final ProductRepository productRepository;

    /**
     * Find matching product in database
     * Strategy:
     * 1. Try exact match by brand + model
     * 2. Try fuzzy match by normalized name
     */
    public Optional<Product> findMatchingProduct(NormalizedProduct normalized) {
        if (normalized.getBrand() != null && normalized.getModel() != null) {
            Optional<Product> exactMatch = findByBrandAndModel(
                    normalized.getBrand(),
                    normalized.getModel()
            );
            if (exactMatch.isPresent()) {
                log.debug("Found exact match by brand+model: {}", exactMatch.get().getNormalizedName());
                return exactMatch;
            }
        }

        if (normalized.getNormalizedName() != null) {
            Optional<Product> fuzzyMatch = findByNormalizedName(normalized.getNormalizedName());
            if (fuzzyMatch.isPresent()) {
                log.debug("Found fuzzy match by name: {}", fuzzyMatch.get().getNormalizedName());
                return fuzzyMatch;
            }
        }

        log.debug("No matching product found for: {}", normalized.getRawTitle());
        return Optional.empty();
    }

    private Optional<Product> findByBrandAndModel(String brand, String model) {
        String normalizedBrand = TextNormalizationUtil.normalize(brand);
        String normalizedModel = TextNormalizationUtil.normalize(model);

        List<Product> candidates = productRepository.findByBrandIgnoreCase(brand);

        return candidates.stream()
                .filter(p -> {
                    String candidateModel = TextNormalizationUtil.normalize(p.getModel());
                    return candidateModel.equals(normalizedModel);
                })
                .findFirst();
    }

    private Optional<Product> findByNormalizedName(String normalizedName) {
        String searchName = TextNormalizationUtil.normalize(normalizedName);

        List<Product> allProducts = productRepository.findAll();

        return allProducts.stream()
                .filter(p -> {
                    String candidateName = TextNormalizationUtil.normalize(p.getNormalizedName());
                    return calculateSimilarity(searchName, candidateName) > 0.85;
                })
                .findFirst();
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());

        return 1.0 - ((double) distance / maxLength);
    }

    private int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(
                                dp[i - 1][j] + 1,
                                dp[i][j - 1] + 1
                        ),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    public boolean areProductsSame(Product p1, Product p2) {
        if (p1.getBrand() != null && p2.getBrand() != null &&
                p1.getModel() != null && p2.getModel() != null) {

            String brand1 = TextNormalizationUtil.normalize(p1.getBrand());
            String brand2 = TextNormalizationUtil.normalize(p2.getBrand());
            String model1 = TextNormalizationUtil.normalize(p1.getModel());
            String model2 = TextNormalizationUtil.normalize(p2.getModel());

            if (brand1.equals(brand2) && model1.equals(model2)) {
                return true;
            }
        }

        String name1 = TextNormalizationUtil.normalize(p1.getNormalizedName());
        String name2 = TextNormalizationUtil.normalize(p2.getNormalizedName());

        return calculateSimilarity(name1, name2) > 0.85;
    }
}
