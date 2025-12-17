package com.samir.pricecomparator.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class ElasticsearchMetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong documentCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        Gauge.builder("pricecomparator_elasticsearch_documents_total", documentCount, AtomicLong::get)
            .description("Total documents in Elasticsearch index")
            .register(meterRegistry);
    }

    public void recordIndexOperation(String operation, String result) {
        Counter.builder("pricecomparator_elasticsearch_index_operations_total")
            .description("Elasticsearch index operations")
            .tag("operation", operation)
            .tag("result", result)
            .register(meterRegistry)
            .increment();
    }

    public Timer.Sample startIndexTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordIndexDuration(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("pricecomparator_elasticsearch_index_duration_seconds")
            .description("Elasticsearch indexing duration")
            .tag("operation", operation)
            .register(meterRegistry));
    }

    public void updateDocumentCount(long count) {
        documentCount.set(count);
    }

    public Timer.Sample startQueryTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordQueryDuration(Timer.Sample sample) {
        sample.stop(Timer.builder("pricecomparator_elasticsearch_query_duration_seconds")
            .description("Elasticsearch query duration")
            .register(meterRegistry));
    }
}
