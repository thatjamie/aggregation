package com.aggregation.scanner.producer;

import com.aggregation.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Publisher for sending tasks to Kafka.
 */
public class TaskPublisher {

    private static final Logger logger = LoggerFactory.getLogger(TaskPublisher.class);

    private final KafkaProducer<String, String> kafkaProducer;
    private final ObjectMapper objectMapper;
    private final String topic;

    public TaskPublisher(KafkaProducer<String, String> kafkaProducer, String topic) {
        this.kafkaProducer = kafkaProducer;
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Publish a task to Kafka.
     *
     * @param task the task to publish
     * @return CompletableFuture that completes when the record is sent
     */
    public CompletableFuture<Void> publishTask(Task task) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        try {
            String taskJson = objectMapper.writeValueAsString(task);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                task.getId(),
                taskJson
            );

            kafkaProducer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception == null) {
                        logger.debug(
                            "Published task {} to partition {} at offset {}",
                            task.getId(),
                            metadata.partition(),
                            metadata.offset()
                        );
                        future.complete(null);
                    } else {
                        logger.error("Failed to publish task {}: {}", task.getId(), exception.getMessage());
                        future.completeExceptionally(exception);
                    }
                }
            });

        } catch (Exception e) {
            logger.error("Error serializing task {}", task.getId(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * Flush any pending records.
     */
    public void flush() {
        kafkaProducer.flush();
    }
}
