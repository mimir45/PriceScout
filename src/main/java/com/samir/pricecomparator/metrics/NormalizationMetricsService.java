package com.samir.pricecomparator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NormalizationMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordNormalizationAttempt(String result) {
        Counter.builder("pricecomparator_normalization_attempts_total")
            .description("Product normalization attempts")
            .tag("result", result)
            .register(meterRegistry)
            .increment();
    }

    public void recordBrandDetected(String brand) {
        if (brand != null && !brand.isEmpty()) {
            Counter.builder("pricecomparator_normalization_brand_detected_total")
                .description("Brands detected during normalization")
                .tag("brand", brand)
                .register(meterRegistry)
                .increment();
        }
    }

    public void recordColorDetected(String color) {
        if (color != null && !color.isEmpty()) {
            Counter.builder("pricecomparator_normalization_color_detected_total")
                .description("Colors detected during normalization")
                .tag("color", color)
                .register(meterRegistry)
                .increment();
        }
    }

    public void recordNormalizationError(String errorType) {
        Counter.builder("pricecomparator_normalization_errors_total")
            .description("Normalization errors by type")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment();
    }
}
