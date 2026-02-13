package com.aggregation.api.controller;

import com.aggregation.api.service.AggregationService;
import com.aggregation.model.AggregatedData;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST controller for querying aggregated data.
 */
@RestController
@RequestMapping("/api/v1/aggregations")
public class AggregationController {

    private final AggregationService aggregationService;

    public AggregationController(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    /**
     * Get aggregated data by key.
     *
     * @param key The aggregation key
     * @return The aggregated data
     */
    @GetMapping("/{key}")
    public Mono<AggregatedData> getByKey(@PathVariable String key) {
        return aggregationService.getByKey(key);
    }

    /**
     * Get aggregated data by key with detailed metrics.
     *
     * @param key The aggregation key
     * @return The aggregated data with metrics
     */
    @GetMapping("/{key}/details")
    public Mono<AggregatedData> getByKeyWithDetails(@PathVariable String key) {
        return aggregationService.getByKeyWithDetails(key);
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("OK");
    }
}
