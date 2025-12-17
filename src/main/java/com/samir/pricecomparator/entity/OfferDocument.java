package com.samir.pricecomparator.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Document(indexName = "product_offers")
@Setting(replicas = 1, shards = 3)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long productId;

    @Field(type = FieldType.Long)
    private Long shopId;

    @Field(type = FieldType.Keyword)
    private String shopCode;

    @Field(type = FieldType.Text)
    private String shopName;

    // Full-text search fields with analyzer
    @Field(type = FieldType.Text, analyzer = "standard")
    private String productName;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String normalizedName;

    @Field(type = FieldType.Keyword)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String model;

    @Field(type = FieldType.Keyword)
    private String category;

    // Filterable fields
    @Field(type = FieldType.Keyword)
    private String condition;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Double)
    private BigDecimal oldPrice;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(type = FieldType.Keyword)
    private String imageUrl;

    @Field(type = FieldType.Boolean)
    private boolean inStock;

    @Field(type = FieldType.Boolean)
    private boolean active;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate firstSeenAt;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate lastSeenAt;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate indexedAt;
}
