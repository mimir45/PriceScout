package com.samir.pricecomparator.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferSearchRequest {

    private String query;
    private String condition;      // NEW/USED
    private String color;
    private List<String> shopCodes;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    @Builder.Default
    private Integer limit = 3;     // Default 3 cheapest offers

    public Integer getLimit() {
        if (limit == null || limit <= 0) {
            return 3;
        }
        return Math.min(limit, 100); // Max 100 results
    }
}
