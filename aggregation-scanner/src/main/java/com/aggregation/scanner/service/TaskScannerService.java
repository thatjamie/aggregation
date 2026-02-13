package com.aggregation.scanner.service;

import com.aggregation.model.Task;
import com.aggregation.model.TaskStatus;
import com.aggregation.scanner.producer.TaskPublisher;
import com.aggregation.scanner.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for scanning and publishing tasks to Kafka.
 */
public class TaskScannerService {

    private static final Logger logger = LoggerFactory.getLogger(TaskScannerService.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long STALE_TASK_THRESHOLD_MS = 3600000; // 1 hour

    private final TaskRepository taskRepository;
    private final TaskPublisher taskPublisher;

    public TaskScannerService(
        TaskRepository taskRepository,
        org.apache.kafka.clients.producer.KafkaProducer<String, String> kafkaProducer,
        com.fasterxml.jackson.databind.ObjectMapper objectMapper,
        String topic
    ) {
        this.taskRepository = taskRepository;
        this.taskPublisher = new TaskPublisher(kafkaProducer, topic);
    }

    /**
     * Scan for pending tasks and publish them to Kafka.
     */
    public void scanAndPublishTasks() {
        logger.debug("Starting task scan...");

        try {
            // Find pending tasks
            List<Task> pendingTasks = taskRepository.findPendingTasks(
                Instant.now(),
                DEFAULT_BATCH_SIZE
            );

            if (pendingTasks.isEmpty()) {
                logger.debug("No pending tasks found");
            } else {
                logger.info("Found {} pending tasks", pendingTasks.size());
            }

            // Process each task
            int publishedCount = 0;
            int failedCount = 0;

            for (Task task : pendingTasks) {
                try {
                    // Mark task as processing atomically
                    boolean marked = taskRepository.markAsProcessing(task.getId());

                    if (marked) {
                        // Publish to Kafka
                        CompletableFuture<Void> future = taskPublisher.publishTask(task);
                        future.whenComplete((v, ex) -> {
                            if (ex != null) {
                                logger.error("Failed to publish task {}", task.getId(), ex);
                                handleTaskError(task, ex);
                            }
                        });
                        publishedCount++;
                    } else {
                        logger.debug("Task {} was already marked as processing by another scanner", task.getId());
                    }

                } catch (Exception e) {
                    logger.error("Error processing task {}: {}", task.getId(), e.getMessage(), e);
                    handleTaskError(task, e);
                    failedCount++;
                }
            }

            // Flush to ensure all messages are sent
            taskPublisher.flush();

            if (publishedCount > 0 || failedCount > 0) {
                logger.info("Task scan complete: {} published, {} failed", publishedCount, failedCount);
            }

            // Check for stale tasks
            checkAndResetStaleTasks();

        } catch (Exception e) {
            logger.error("Error in task scanning", e);
        }
    }

    /**
     * Check for stale tasks and reset them to PENDING status.
     */
    private void checkAndResetStaleTasks() {
        try {
            Instant threshold = Instant.now().minusMillis(STALE_TASK_THRESHOLD_MS);
            List<Task> staleTasks = taskRepository.findStaleTasks(threshold);

            if (!staleTasks.isEmpty()) {
                logger.warn("Found {} stale tasks, resetting to PENDING", staleTasks.size());
                for (Task task : staleTasks) {
                    taskRepository.resetStaleTask(task);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking for stale tasks", e);
        }
    }

    /**
     * Handle task processing error.
     *
     * @param task the task that failed
     * @param error the error that occurred
     */
    private void handleTaskError(Task task, Throwable error) {
        try {
            String errorMessage = error != null ? error.getMessage() : "Unknown error";
            taskRepository.markAsFailed(task, errorMessage);
        } catch (Exception e) {
            logger.error("Error updating failed task {}: {}", task.getId(), e.getMessage());
        }
    }
}
