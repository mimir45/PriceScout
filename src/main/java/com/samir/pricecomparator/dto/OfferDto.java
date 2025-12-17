package com.samir.pricecomparator.dto;

import java.math.BigDecimal;

public record OfferDto(
    Long offerId,
    String shopCode,
    String shopName,
    String title,
    String normalizedName,
    String brand,
    String model,
    String category,
    String color,
    String condition,
    BigDecimal price,
    BigDecimal oldPrice,
    String currency,
    String url,
    String imageUrl,
    boolean inStock
) {}

