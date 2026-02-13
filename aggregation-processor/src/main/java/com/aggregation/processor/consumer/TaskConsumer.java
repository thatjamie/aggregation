package com.aggregation.processor.consumer;

import com.aggregation.model.Task;
import com.aggregation.model.TaskStatus;
import com.aggregation.processor.model.AggregationResult;
import com.aggregation.processor.service.AggregationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;

/**
 * Consumer for processing aggregation tasks from Kafka.
 */
public class TaskConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TaskConsumer.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getProperty(
        "kafka.bootstrap.servers",
        "localhost:9092"
    );
    private static final String MONGO_URI = System.getProperty(
        "mongo.uri",
        "mongodb://localhost:27017"
    );
    private static final String MONGO_DATABASE = System.getProperty(
        "mongo.database",
        "aggregation_db"
    );
    private static final String TASKS_TOPIC = System.getProperty(
        "kafka.topic.tasks",
        "aggregation.tasks"
    );
    private static final String CONSUMER_GROUP_ID = System.getProperty(
        "kafka.consumer.group.id",
        "aggregation-processor"
    );
    private static final long POLL_TIMEOUT_MS = Long.parseLong(
        System.getProperty("kafka.consumer.poll.timeout.ms", "1000")
    );

    private final KafkaConsumer<String, String> kafkaConsumer;
    private final ObjectMapper objectMapper;
    private final AggregationService aggregationService;
    private final MongoCollection<Document> tasksCollection;

    public TaskConsumer() {
        // Initialize Kafka consumer
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        this.kafkaConsumer = new KafkaConsumer<>(props);
        this.kafkaConsumer.subscribe(Collections.singletonList(TASKS_TOPIC));

        // Initialize ObjectMapper
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Initialize MongoDB
        MongoClient mongoClient = MongoClients.create(MONGO_URI);
        MongoDatabase mongoDatabase = mongoClient.getDatabase(MONGO_DATABASE);
        this.aggregationService = new AggregationService(mongoDatabase);
        this.tasksCollection = mongoDatabase.getCollection("aggregation_tasks");

        logger.info("TaskConsumer initialized");
        logger.info("Kafka bootstrap servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        logger.info("MongoDB URI: {}", MONGO_URI);
        logger.info("MongoDB database: {}", MONGO_DATABASE);
        logger.info("Topic: {}", TASKS_TOPIC);
        logger.info("Consumer group: {}", CONSUMER_GROUP_ID);
    }

    /**
     * Start consuming and processing tasks.
     */
    public void start() {
        logger.info("Starting task consumer...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down task consumer...");
            kafkaConsumer.close();
            logger.info("Task consumer stopped.");
        }));

        while (true) {
            try {
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));

                if (records.isEmpty()) {
                    continue;
                }

                logger.info("Received {} records", records.count());

                for (ConsumerRecord<String, String> record : records) {
                    String taskId = record.key();
                    String taskJson = record.value();

                    logger.info("Processing task: {}", taskId);

                    try {
                        // Deserialize task
                        Task task = objectMapper.readValue(taskJson, Task.class);

                        // Execute aggregation
                        AggregationResult result = aggregationService.executeAggregation(task);

                        // Update task status to COMPLETED
                        updateTaskStatus(task.getId(), TaskStatus.COMPLETED, null);

                        logger.info("Task {} completed successfully. Processed {} documents",
                            taskId, result.getProcessedCount());

                        // Commit offset after successful processing
                        kafkaConsumer.commitSync();

                    } catch (Exception e) {
                        logger.error("Error processing task {}: {}", taskId, e.getMessage(), e);

                        // Try to get task ID for status update
                        try {
                            Task task = objectMapper.readValue(taskJson, Task.class);
                            updateTaskStatus(task.getId(), TaskStatus.FAILED, e.getMessage());
                        } catch (Exception ex) {
                            logger.error("Error updating task status", ex);
                        }

                        // Still commit offset to move past the failed message
                        kafkaConsumer.commitSync();
                    }
                }

            } catch (Exception e) {
                logger.error("Error in consumer loop", e);
            }
        }
    }

    /**
     * Update task status in MongoDB.
     *
     * @param taskId the task ID
     * @param status the new status
     * @param errorMessage optional error message
     */
    private void updateTaskStatus(String taskId, TaskStatus status, String errorMessage) {
        try {
            Bson filter = Filters.eq("_id", taskId);
            Bson statusUpdate = Updates.set("status", status.toString());

            if (status == TaskStatus.COMPLETED) {
                Bson completedAtUpdate = Updates.set("completedAt", Instant.now());
                tasksCollection.updateOne(filter, Updates.combine(statusUpdate, completedAtUpdate));
            } else if (status == TaskStatus.FAILED) {
                Bson completedAtUpdate = Updates.set("completedAt", Instant.now());
                Bson errorUpdate = Updates.set("errorMessage", errorMessage != null ? errorMessage : "Unknown error");
                Bson retryUpdate = Updates.inc("retryCount", 1);
                tasksCollection.updateOne(filter, Updates.combine(statusUpdate, completedAtUpdate, errorUpdate, retryUpdate));
            } else {
                tasksCollection.updateOne(filter, statusUpdate);
            }

            logger.debug("Updated task {} status to {}", taskId, status);

        } catch (Exception e) {
            logger.error("Error updating task {} status: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * Main entry point for the task consumer.
     */
    public static void main(String[] args) {
        TaskConsumer consumer = new TaskConsumer();
        consumer.start();
    }
}
