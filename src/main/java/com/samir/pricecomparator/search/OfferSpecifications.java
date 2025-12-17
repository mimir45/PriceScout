package com.samir.pricecomparator.search;

import com.samir.pricecomparator.entity.ProductOffer;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class OfferSpecifications {

  private OfferSpecifications() {
  }

  public static Specification<ProductOffer> isActiveAndInStock() {
    return (root, cq, cb) -> cb.and(
        cb.isTrue(root.get("isActive")),
        cb.isTrue(root.get("inStock"))
    );
  }

  public static Specification<ProductOffer> queryLike(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    String q = "%" + query.toLowerCase() + "%";

    return (root, cq, cb) -> {
      var productJoin = root.join("product"); // Product
      var titleLike = cb.like(cb.lower(root.get("title")), q);
      var normalizedLike = cb.like(cb.lower(productJoin.get("normalizedName")), q);
      return cb.or(titleLike, normalizedLike);
    };
  }

  public static Specification<ProductOffer> hasCondition(String condition) {
    if (condition == null || condition.isBlank()) {
      return null;
    }
    String value = condition.toUpperCase(); // expect NEW / USED
    return (root, cq, cb) -> cb.equal(cb.upper(root.get("condition")), value);
  }

  public static Specification<ProductOffer> hasColor(String color) {
    if (color == null || color.isBlank()) {
      return null;
    }
    String value = color.toLowerCase();
    return (root, cq, cb) ->
        cb.equal(cb.lower(root.get("color")), value);
  }

  public static Specification<ProductOffer> shopIn(List<String> shopCodes) {
    if (shopCodes == null || shopCodes.isEmpty()) {
      return null;
    }
    List<String> upperCodes = shopCodes.stream()
        .map(String::toUpperCase)
        .toList();

    return (root, cq, cb) -> {
      var shopJoin = root.join("shop");
      return shopJoin.get("code").in(upperCodes);
    };
  }

  public static Specification<ProductOffer> priceBetween(BigDecimal min, BigDecimal max) {
    if (min == null && max == null) {
      return null;
    }

    return (root, cq, cb) -> {
      if (min != null && max != null) {
        return cb.between(root.get("price"), min, max);
      } else if (min != null) {
        return cb.greaterThanOrEqualTo(root.get("price"), min);
      } else {
        return cb.lessThanOrEqualTo(root.get("price"), max);
      }
    };
  }
}
