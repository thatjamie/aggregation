package com.aggregation.api.service;

import com.aggregation.model.AggregatedData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service for managing aggregated data using Redis as cache.
 */
@Service
public class AggregationService {

    private static final Logger logger = LoggerFactory.getLogger(AggregationService.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration cacheTtl = Duration.ofHours(24);

    public AggregationService(ReactiveRedisTemplate<String, String> redisTemplate,
                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = "agg:";
    }

    /**
     * Get aggregated data by key from Redis cache.
     */
    public Mono<AggregatedData> getByKey(String key) {
        String redisKey = keyPrefix + key;

        return redisTemplate.opsForValue()
            .get(redisKey)
            .flatMap(this::parseAggregatedData)
            .switchIfEmpty(Mono.empty());
    }

    /**
     * Get aggregated data by key with detailed metrics.
     */
    public Mono<AggregatedData> getByKeyWithDetails(String key) {
        return getByKey(key);
    }

    /**
     * Listen to aggregated data from Kafka and store in Redis.
     */
    @KafkaListener(
        topics = "${aggregation.output-topic}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAggregatedData(@Payload String data,
                                     @Header(KafkaHeaders.RECEIVED_KEY) String key) {
        try {
            logger.debug("Received aggregated data: key={}", key);

            // Store in Redis with TTL
            String redisKey = keyPrefix + key;
            redisTemplate.opsForValue()
                .set(redisKey, data, cacheTtl)
                .doOnSuccess(success -> logger.debug("Stored aggregated data: key={}", key))
                .doOnError(error -> logger.error("Error storing aggregated data: key={}", key, error))
                .subscribe();

        } catch (Exception e) {
            logger.error("Error processing aggregated data", e);
        }
    }

    private Mono<AggregatedData> parseAggregatedData(String json) {
        try {
            AggregatedData data = objectMapper.readValue(json, AggregatedData.class);
            return Mono.just(data);
        } catch (Exception e) {
            logger.error("Error parsing aggregated data", e);
            return Mono.error(e);
        }
    }
}
