package com.samir.pricecomparator.service.search;

import static com.samir.pricecomparator.search.OfferSpecifications.*;

import com.samir.pricecomparator.dto.OfferDto;
import com.samir.pricecomparator.entity.Product;
import com.samir.pricecomparator.entity.ProductOffer;
import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.repository.ProductOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JpaSearchService {

    private final ProductOfferRepository offerRepository;


    @Transactional(readOnly = true)
    public List<OfferDto> search(
            String query,
            String condition,
            String color,
            List<String> shopCodes,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int limit
    ) {
        log.debug("Searching via JPA: query='{}', condition='{}', color='{}', shops={}, minPrice={}, maxPrice={}, limit={}",
                query, condition, color, shopCodes, minPrice, maxPrice, limit);

        if (limit <= 0) {
            limit = 3;
        }

        Specification<ProductOffer> spec = isActiveAndInStock();

        var querySpec = queryLike(query);
        if (querySpec != null) spec = spec.and(querySpec);

        var conditionSpec = hasCondition(condition);
        if (conditionSpec != null) spec = spec.and(conditionSpec);

        var colorSpec = hasColor(color);
        if (colorSpec != null) spec = spec.and(colorSpec);

        var shopSpec = shopIn(shopCodes);
        if (shopSpec != null) spec = spec.and(shopSpec);

        var priceSpec = priceBetween(minPrice, maxPrice);
        if (priceSpec != null) spec = spec.and(priceSpec);

        PageRequest pageRequest = PageRequest.of(
                0,
                limit,
                Sort.by(Sort.Direction.ASC, "price")
        );

        Page<ProductOffer> page = offerRepository.findAll(spec, pageRequest);

        List<OfferDto> offers = page.stream()
                .map(this::toDto)
                .toList();

        log.debug("JPA search returned {} results", offers.size());
        return offers;
    }


    private OfferDto toDto(ProductOffer offer) {
        Product product = offer.getProduct();
        Shop shop = offer.getShop();

        return new OfferDto(
                offer.getId(),
                shop.getCode(),
                shop.getName(),
                offer.getTitle(),
                product.getNormalizedName(),
                product.getBrand(),
                product.getModel(),
                product.getCategory(),
                offer.getColor(),
                offer.getCondition(),
                offer.getPrice(),
                offer.getOldPrice(),
                offer.getCurrency(),
                offer.getUrl(),
                product.getMainImageUrl() != null ? product.getMainImageUrl() : offer.getUrl(),
                offer.isInStock()
        );
    }
}
