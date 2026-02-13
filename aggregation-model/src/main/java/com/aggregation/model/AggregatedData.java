package com.aggregation.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregated data container for stream processing results.
 */
public class AggregatedData {

    @JsonProperty("aggregate_key")
    private String aggregateKey;

    @JsonProperty("count")
    private final AtomicLong count = new AtomicLong(0);

    @JsonProperty("first_seen")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant firstSeen;

    @JsonProperty("last_updated")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastUpdated;

    @JsonProperty("metrics")
    private Map<String, Object> metrics = new ConcurrentHashMap<>();

    public AggregatedData() {
    }

    public AggregatedData(String aggregateKey) {
        this.aggregateKey = aggregateKey;
        this.firstSeen = Instant.now();
        this.lastUpdated = Instant.now();
    }

    public AggregatedData addEvent(Event event) {
        count.incrementAndGet();
        this.lastUpdated = Instant.now();

        // Extract metrics from event data
        if (event.getData() != null && event.getData().getPayload() != null) {
            event.getData().getPayload().forEach((key, value) -> {
                metrics.merge(key, value, (oldValue, newValue) -> {
                    if (oldValue instanceof Number && newValue instanceof Number) {
                        return ((Number) oldValue).doubleValue() + ((Number) newValue).doubleValue();
                    }
                    return oldValue;
                });
            });
        }

        return this;
    }

    public String getAggregateKey() {
        return aggregateKey;
    }

    public void setAggregateKey(String aggregateKey) {
        this.aggregateKey = aggregateKey;
    }

    public long getCount() {
        return count.get();
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
}
