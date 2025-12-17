package com.samir.pricecomparator.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapedProductDto {

    private String shopCode;
    private String title;
    private String url;
    private BigDecimal price;
    private BigDecimal oldPrice;
    private String currency;
    private String condition;  // NEW/USED
    private String color;
    private String imageUrl;
    private boolean inStock;
}
