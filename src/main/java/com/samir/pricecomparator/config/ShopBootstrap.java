package com.samir.pricecomparator.config;

import com.samir.pricecomparator.entity.Shop;
import com.samir.pricecomparator.repository.ShopRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopBootstrap {

  private final ShopRepository shopRepository;

  @PostConstruct
  public void initShops() {
    if (shopRepository.count() > 0) {
      return;
    }

    log.info("[ShopBootstrap] Initializing default shops");

    LocalDateTime now = LocalDateTime.now();

    shopRepository.save(Shop.builder()
        .code("KONTAKT")
        .name("Kontakt Home")
        .baseUrl("https://kontakt.az")
        .active(true)
        .createdAt(now)
        .updatedAt(now)
        .build());

    shopRepository.save(Shop.builder()
        .code("IRSHAD")
        .name("IRSHAD")
        .baseUrl("https://irshad.az")
        .active(true)
        .createdAt(now)
        .updatedAt(now)
        .build());

    shopRepository.save(Shop.builder()
        .code("BAKU_ELECTRONICS")
        .name("Baku Electronics")
        .baseUrl("https://www.bakuelectronics.az")
        .active(true)
        .createdAt(now)
        .updatedAt(now)
        .build());

    shopRepository.save(Shop.builder()
        .code("SHOP3")
        .name("Shop 3")
        .baseUrl("https://shop3.example.az")
        .createdAt(now)
        .updatedAt(now)
        .build());
  }
}
