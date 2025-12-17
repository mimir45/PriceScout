package com.samir.pricecomparator.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedProduct {

    private String shopCode;
    private String rawTitle;
    private String normalizedName;
    private String brand;
    private String model;
    private String category;
    private String url;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private String currency;
    private String condition;
    private String color;
    private String imageUrl;
    private boolean inStock;
}
