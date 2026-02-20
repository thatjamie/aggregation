package com.aggregation.scanner;

import com.aggregation.scanner.config.MongoConfig;
import com.aggregation.scanner.repository.TaskRepository;
import com.aggregation.scanner.service.TaskScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main application for the task scanner service.
 * Scans MongoDB for pending tasks and publishes them to Kafka.
 */
public class TaskScannerApplication {

    private static final Logger logger = LoggerFactory.getLogger(TaskScannerApplication.class);

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
    private static final long SCAN_INTERVAL_MS = Long.parseLong(
        System.getProperty("scanner.interval.ms", "60000")
    );

    public static void main(String[] args) {
        logger.info("Starting Task Scanner Application...");
        logger.info("Kafka bootstrap servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        logger.info("MongoDB URI: {}", MONGO_URI);
        logger.info("MongoDB database: {}", MONGO_DATABASE);
        logger.info("Scan interval: {} ms", SCAN_INTERVAL_MS);

        try {
            // Initialize MongoDB configuration
            MongoConfig mongoConfig = new MongoConfig(MONGO_URI, MONGO_DATABASE);
            TaskRepository taskRepository = new TaskRepository(mongoConfig);

            // Initialize Kafka producer
            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

            KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(producerProps);

            // Initialize ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            // Create and start scanner service
            TaskScannerService scannerService = new TaskScannerService(
                taskRepository,
                kafkaProducer,
                objectMapper,
                TASKS_TOPIC
            );

            // Schedule periodic scanning
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        scannerService.scanAndPublishTasks();
                    } catch (Exception e) {
                        logger.error("Error during task scan", e);
                    }
                },
                0,
                SCAN_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down Task Scanner Application...");
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                kafkaProducer.close();
                mongoConfig.close();
                logger.info("Task Scanner Application stopped.");
            }));

            logger.info("Task Scanner Application started. Press Ctrl+C to exit.");

            // Keep application running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start Task Scanner Application", e);
            System.exit(1);
        }
    }
}
