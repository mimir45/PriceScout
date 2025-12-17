package com.samir.pricecomparator.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samir.pricecomparator.dto.OfferSearchResponse;
import com.samir.pricecomparator.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheKeyGenerator keyGenerator;
    private final CacheMetricsService cacheMetrics;
    private final ObjectMapper objectMapper;

    private static final String SEARCH_PREFIX = "search:";
    private static final String CHEAPEST_PREFIX = "cheapest:";
    private static final Duration SEARCH_TTL = Duration.ofMinutes(30);
    private static final Duration CHEAPEST_TTL = Duration.ofHours(12);


    public void cacheSearchResult(String query, String condition, String color,
                                   OfferSearchResponse response) {
        String key = keyGenerator.generateSearchKey(query, condition, color);
        try {
            redisTemplate.opsForValue().set(SEARCH_PREFIX + key, response, SEARCH_TTL);
            log.debug("Cached search result: {} (query: {})", key, query);
            cacheMetrics.recordCacheOperation("put", "search", "success");
        } catch (Exception e) {
            log.error("Failed to cache search result", e);
            cacheMetrics.recordCacheOperation("put", "search", "error");
        }
    }

    public Optional<OfferSearchResponse> getCachedSearch(String query, String condition, String color) {
        String key = keyGenerator.generateSearchKey(query, condition, color);
        try {
            Object cached = redisTemplate.opsForValue().get(SEARCH_PREFIX + key);
            if (cached != null) {
                OfferSearchResponse response;
                if (cached instanceof OfferSearchResponse) {
                    response = (OfferSearchResponse) cached;
                } else {
                    response = objectMapper.convertValue(cached, OfferSearchResponse.class);
                }
                log.debug("Cache hit: {} (query: {})", key, query);
                cacheMetrics.recordCacheOperation("get", "search", "hit");
                return Optional.of(response);
            }
            cacheMetrics.recordCacheOperation("get", "search", "miss");
        } catch (Exception e) {
            log.warn("Failed to deserialize cached search (possibly old format): {}", e.getMessage());
            cacheMetrics.recordCacheOperation("get", "search", "error");
            try {
                redisTemplate.delete(SEARCH_PREFIX + key);
                log.debug("Deleted incompatible cache entry: {}", key);
            } catch (Exception deleteEx) {
                log.warn("Failed to delete incompatible cache entry: {}", deleteEx.getMessage());
            }
        }
        log.debug("Cache miss: {} (query: {})", key, query);
        return Optional.empty();
    }

    public void cacheCheapestOffers(String category, Object offers) {
        String key = CHEAPEST_PREFIX + category;
        try {
            redisTemplate.opsForValue().set(key, offers, CHEAPEST_TTL);
            log.debug("Cached cheapest offers for category: {}", category);
        } catch (Exception e) {
            log.error("Failed to cache cheapest offers", e);
        }
    }

    public Optional<Object> getCachedCheapest(String category) {
        String key = CHEAPEST_PREFIX + category;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Cache hit for cheapest: {}", category);
                return Optional.of(cached);
            }
        } catch (Exception e) {
            log.error("Failed to retrieve cached cheapest", e);
        }
        return Optional.empty();
    }

    public void invalidateAllSearchCaches() {
        try {
            Set<String> keys = redisTemplate.keys(SEARCH_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                cacheMetrics.recordCacheEviction("search", keys.size());
                log.info("Invalidated {} search cache keys", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to invalidate search caches", e);
        }
    }

    public void invalidateCheapestCaches() {
        try {
            Set<String> keys = redisTemplate.keys(CHEAPEST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                cacheMetrics.recordCacheEviction("cheapest", keys.size());
                log.info("Invalidated {} cheapest cache keys", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to invalidate cheapest caches", e);
        }
    }

    public void invalidateAllCaches() {
        invalidateAllSearchCaches();
        invalidateCheapestCaches();
    }


    public CacheStats getStats() {
        try {
            Set<String> searchKeys = redisTemplate.keys(SEARCH_PREFIX + "*");
            Set<String> cheapestKeys = redisTemplate.keys(CHEAPEST_PREFIX + "*");

            return new CacheStats(
                    searchKeys != null ? searchKeys.size() : 0,
                    cheapestKeys != null ? cheapestKeys.size() : 0
            );
        } catch (Exception e) {
            log.error("Failed to get cache stats", e);
            return new CacheStats(0, 0);
        }
    }


    public record CacheStats(int searchCacheCount, int cheapestCacheCount) {
    }
}
