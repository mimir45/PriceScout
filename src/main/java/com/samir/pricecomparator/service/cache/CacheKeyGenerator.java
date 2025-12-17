package com.samir.pricecomparator.service.cache;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CacheKeyGenerator {

    public String generateSearchKey(String query, String condition, String color) {
        StringBuilder sb = new StringBuilder();

        sb.append(normalize(query));
        sb.append("|");
        sb.append(normalize(condition));
        sb.append("|");
        sb.append(normalize(color));

        String raw = sb.toString();

        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String generateSearchKey(String query, String condition, String color,
                                     List<String> shopCodes, Double minPrice, Double maxPrice) {
        StringBuilder sb = new StringBuilder();

        sb.append(normalize(query));
        sb.append("|");
        sb.append(normalize(condition));
        sb.append("|");
        sb.append(normalize(color));
        sb.append("|");

        if (shopCodes != null && !shopCodes.isEmpty()) {
            sb.append(String.join(",", shopCodes.stream().sorted().toList()));
        }
        sb.append("|");

        sb.append(minPrice != null ? minPrice.toString() : "");
        sb.append("|");
        sb.append(maxPrice != null ? maxPrice.toString() : "");

        String raw = sb.toString();
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase();
    }
}
