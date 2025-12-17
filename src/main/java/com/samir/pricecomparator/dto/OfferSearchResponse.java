package com.samir.pricecomparator.dto;

import java.util.List;

public record OfferSearchResponse(
    String query,
    long totalMatches,
    List<OfferDto> offers
) {}

