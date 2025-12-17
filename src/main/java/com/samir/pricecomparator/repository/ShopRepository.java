package com.samir.pricecomparator.repository;

import com.samir.pricecomparator.entity.Shop;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopRepository extends JpaRepository<Shop, Long> {
  Optional<Shop> findByCodeIgnoreCase(String code);
  List<Shop> findByActiveTrue();
}
