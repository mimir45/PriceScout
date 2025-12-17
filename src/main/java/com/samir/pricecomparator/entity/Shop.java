package com.samir.pricecomparator.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "shops")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;   // e.g. KONTAKT

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "base_url", length = 255)
  private String baseUrl;

  @Column(name = "logo_url", length = 255)
  private String logoUrl;

  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "last_scraped_at")
  private LocalDateTime lastScrapedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
