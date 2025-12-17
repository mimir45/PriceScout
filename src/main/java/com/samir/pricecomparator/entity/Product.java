package com.samir.pricecomparator.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "normalized_name", nullable = false, length = 255)
  private String normalizedName;

  @Column(length = 100)
  private String brand;

  @Column(length = 100)
  private String model;

  @Column(length = 100)
  private String category; // SMARTPHONE

  @Column(name = "main_image_url", length = 255)
  private String mainImageUrl;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
