package com.samir.pricecomparator.controller;

import com.samir.pricecomparator.dto.OfferSearchResponse;
import com.samir.pricecomparator.service.search.SearchOrchestrator;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OfferSearchController {

  private final SearchOrchestrator searchOrchestrator;

  @GetMapping("/api/offers/search")
  public OfferSearchResponse searchOffers(
      @RequestParam String query,
      @RequestParam(required = false) String condition,
      @RequestParam(required = false) String color,
      @RequestParam(name = "shop", required = false) List<String> shopCodes,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "3") int limit
  ) {
    return searchOrchestrator.search(
        query,
        condition,
        color,
        shopCodes,
        minPrice,
        maxPrice,
        limit
    );
  }
}
