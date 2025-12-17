package com.samir.pricecomparator.repository;

import com.samir.pricecomparator.entity.OfferDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfferElasticsearchRepository extends ElasticsearchRepository<OfferDocument, Long> {

    Page<OfferDocument> findByProductNameContainingOrNormalizedNameContaining(
            String productName, String normalizedName, Pageable pageable);

    @Query("{\"bool\": {\"must\": [{\"bool\": {\"should\": [{\"match_phrase\": {\"normalizedName\": {\"query\": \"?0\", \"boost\": 10.0}}}, {\"match_phrase\": {\"model\": {\"query\": \"?0\", \"boost\": 8.0}}}, {\"match\": {\"normalizedName\": {\"query\": \"?0\", \"boost\": 5.0, \"fuzziness\": \"AUTO\"}}}, {\"match\": {\"brand\": {\"query\": \"?0\", \"boost\": 6.0}}}, {\"match\": {\"model\": {\"query\": \"?0\", \"boost\": 6.0, \"fuzziness\": \"AUTO\"}}}, {\"match\": {\"productName\": {\"query\": \"?0\", \"boost\": 3.0}}}], \"minimum_should_match\": 1}}], \"filter\": [{\"term\": {\"active\": true}}]}}")
    Page<OfferDocument> fuzzySearchActive(String query, Pageable pageable);

    Page<OfferDocument> findByActiveTrue(Pageable pageable);

    Page<OfferDocument> findByShopCodeAndActiveTrue(String shopCode, Pageable pageable);
}
