package com.samir.pricecomparator.repository;

import com.samir.pricecomparator.entity.Product;
import com.samir.pricecomparator.entity.ProductOffer;
import com.samir.pricecomparator.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long>, JpaSpecificationExecutor<ProductOffer> {

    Optional<ProductOffer> findByProductAndShop(Product product, Shop shop);

    @Query("SELECT po FROM ProductOffer po JOIN FETCH po.product JOIN FETCH po.shop WHERE po.inStock = true")
    List<ProductOffer> findByInStockTrue();

    List<ProductOffer> findByProduct(Product product);
}

