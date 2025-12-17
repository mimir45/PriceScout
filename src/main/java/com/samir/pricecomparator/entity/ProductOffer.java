package com.samir.pricecomparator.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "product_offers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductOffer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id")
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "shop_id")
  private Shop shop;

  @Column(nullable = false, length = 255)
  private String title;   // raw title from shop

  @Column(nullable = false, length = 500)
  private String url;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(name = "old_price", precision = 12, scale = 2)
  private BigDecimal oldPrice;

  @Column(length = 10)
  private String currency; // AZN

  @Column(length = 20)
  private String condition; // NEW/USED

  @Column(length = 50)
  private String color;

  @Column(length = 50)
  private String availability; // IN_STOCK / OUT_OF_STOCK

  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Column(name = "in_stock", nullable = false)
  private boolean inStock;

  @Column(name = "first_seen_at", nullable = false)
  private LocalDateTime firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private LocalDateTime lastSeenAt;

  @Column(name = "is_active", nullable = false)
  private boolean active;
}
