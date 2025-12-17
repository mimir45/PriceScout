package com.samir.pricecomparator.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class BrandModelParser {

    private static final Pattern IPHONE_PATTERN = Pattern.compile(
            "(iphone)\\s*(\\d+\\s*pro\\s*max|\\d+\\s*pro|\\d+\\s*plus|\\d+|se|air|mini)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SAMSUNG_PATTERN = Pattern.compile(
            "(samsung|galaxy)\\s*(s\\d+\\s*ultra|s\\d+\\s*plus|s\\d+|a\\d+|z\\s*fold\\d*|z\\s*flip\\d*|note\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern XIAOMI_PATTERN = Pattern.compile(
            "(xiaomi|redmi)\\s*(note\\s*\\d+|\\d+[a-z]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern POCO_PATTERN = Pattern.compile(
            "(poco)\\s*(x\\d+[a-z]*|m\\d+[a-z]*|c\\d+[a-z]*|f\\d+[a-z]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HUAWEI_PATTERN = Pattern.compile(
            "(huawei|honor)\\s*(p\\d+[a-z]*|mate\\s*\\d+|nova\\s*\\d+|magic\\d*[a-z]*\\s*pro|magic\\d*[a-z]*|x\\d+[a-z]*|\\d+[a-z]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OPPO_PATTERN = Pattern.compile(
            "(oppo)\\s*(find\\s*[xn]\\d*|reno\\s*\\d+|a\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VIVO_PATTERN = Pattern.compile(
            "(vivo)\\s*(x\\d+|v\\d+|y\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REALME_PATTERN = Pattern.compile(
            "(realme)\\s*(gt\\s*\\d*|\\d+[a-z]*)",
            Pattern.CASE_INSENSITIVE
    );

        private static final Pattern TECNO_PATTERN = Pattern.compile(
                "(tecno)\\s*(spark\\s*go\\s*\\d*[a-z]*|spark\\s*\\d+[a-z]*|camon\\s*\\d+[a-z]*|phantom\\s*[x\\d]*|pova\\s*\\d+[a-z]*)",
                Pattern.CASE_INSENSITIVE
        );
    
        private static final Pattern INFINIX_PATTERN = Pattern.compile(
                "(infinix)\\s*(note\\s*\\d+[a-z]*\\s*pro|note\\s*\\d+[a-z]*|smart\\s*\\d+[a-z]*|hot\\s*\\d+[a-z]*\\s*pro|hot\\s*\\d+[a-z]*)",
                Pattern.CASE_INSENSITIVE
        );

    private static final Pattern MOTOROLA_PATTERN = Pattern.compile(
            "(motorola)\\s*(moto\\s*[ge]\\d+[a-z]*\\s*power\\s*5g|moto\\s*[ge]\\d+[a-z]*\\s*power|moto\\s*[ge]\\d+[a-z]*\\s*5g|moto\\s*[ge]\\d+[a-z]*|edge\\s*\\d+[a-z]*\\s*fusion\\s*5g|edge\\s*\\d+[a-z]*|razr\\s*\\d+[a-z]*)",
            Pattern.CASE_INSENSITIVE
        );
    
    
        public BrandModelResult parse(String title) {
            if (title == null || title.isBlank()) {
                return new BrandModelResult(null, null);
            }
    
            Matcher iphoneMatcher = IPHONE_PATTERN.matcher(title);
            if (iphoneMatcher.find()) {
                return new BrandModelResult("Apple", normalizeModel(iphoneMatcher.group(0)));
            }
    
            Matcher samsungMatcher = SAMSUNG_PATTERN.matcher(title);
            if (samsungMatcher.find()) {
                return new BrandModelResult("Samsung", normalizeModel(samsungMatcher.group(0)));
            }
    
            Matcher xiaomiMatcher = XIAOMI_PATTERN.matcher(title);
            if (xiaomiMatcher.find()) {
                String brand = xiaomiMatcher.group(1);
                String brandCapitalized = brand.substring(0, 1).toUpperCase() + brand.substring(1).toLowerCase();
                return new BrandModelResult(brandCapitalized, normalizeModel(xiaomiMatcher.group(0)));
            }
    
            Matcher pocoMatcher = POCO_PATTERN.matcher(title);
            if (pocoMatcher.find()) {
                return new BrandModelResult("Poco", normalizeModel(pocoMatcher.group(0)));
            }
    
            Matcher huaweiMatcher = HUAWEI_PATTERN.matcher(title);
            if (huaweiMatcher.find()) {
                String brand = huaweiMatcher.group(1);
                String brandCapitalized = brand.substring(0, 1).toUpperCase() + brand.substring(1).toLowerCase();
                return new BrandModelResult(brandCapitalized, normalizeModel(huaweiMatcher.group(0)));
            }
    
            Matcher oppoMatcher = OPPO_PATTERN.matcher(title);
            if (oppoMatcher.find()) {
                return new BrandModelResult("Oppo", normalizeModel(oppoMatcher.group(0)));
            }
    
            Matcher vivoMatcher = VIVO_PATTERN.matcher(title);
            if (vivoMatcher.find()) {
                return new BrandModelResult("Vivo", normalizeModel(vivoMatcher.group(0)));
            }
    
            Matcher realmeMatcher = REALME_PATTERN.matcher(title);
            if (realmeMatcher.find()) {
                return new BrandModelResult("Realme", normalizeModel(realmeMatcher.group(0)));
            }
    
            Matcher tecnoMatcher = TECNO_PATTERN.matcher(title);
            if (tecnoMatcher.find()) {
                return new BrandModelResult("Tecno", normalizeModel(tecnoMatcher.group(0)));
            }

            Matcher infinixMatcher = INFINIX_PATTERN.matcher(title);
            if (infinixMatcher.find()) {
                return new BrandModelResult("Infinix", normalizeModel(infinixMatcher.group(0)));
            }

            Matcher motorolaMatcher = MOTOROLA_PATTERN.matcher(title);
            if (motorolaMatcher.find()) {
                return new BrandModelResult("Motorola", normalizeModel(motorolaMatcher.group(0)));
            }

        return new BrandModelResult(null, null);
    }

    private String normalizeModel(String model) {
        if (model == null) {
            return null;
        }

        model = model.trim().replaceAll("\\s+", " ");

        String[] words = model.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    @Data
    @AllArgsConstructor
    public static class BrandModelResult {
        private String brand;
        private String model;
    }
}
