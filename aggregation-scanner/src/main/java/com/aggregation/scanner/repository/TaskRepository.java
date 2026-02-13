package com.aggregation.scanner.repository;

import com.aggregation.model.Task;
import com.aggregation.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for accessing and updating tasks in MongoDB.
 */
public class TaskRepository {

    private static final Logger logger = LoggerFactory.getLogger(TaskRepository.class);
    private static final String TASKS_COLLECTION = "aggregation_tasks";

    private final MongoCollection<Document> tasksCollection;
    private final ObjectMapper objectMapper;

    public TaskRepository(com.aggregation.scanner.config.MongoConfig mongoConfig) {
        this.tasksCollection = mongoConfig.getDatabase().getCollection(TASKS_COLLECTION);
        this.objectMapper = new ObjectMapper();
        logger.info("TaskRepository initialized with collection: {}", TASKS_COLLECTION);
    }

    /**
     * Find pending tasks scheduled at or before the given time.
     *
     * @param scheduledAt the scheduled time threshold
     * @param batchSize the maximum number of tasks to return
     * @return list of pending tasks
     */
    public List<Task> findPendingTasks(Instant scheduledAt, int batchSize) {
        List<Task> tasks = new ArrayList<>();

        Bson statusFilter = Filters.eq("status", TaskStatus.PENDING.toString());
        Bson scheduledFilter = Filters.lte("scheduledAt", scheduledAt);
        Bson combinedFilter = Filters.and(statusFilter, scheduledFilter);

        try (MongoCursor<Document> cursor = tasksCollection
                .find(combinedFilter)
                .limit(batchSize)
                .iterator()) {

            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Task task = documentToTask(doc);
                tasks.add(task);
            }
        }

        logger.debug("Found {} pending tasks", tasks.size());
        return tasks;
    }

    /**
     * Update task status to PROCESSING and set started timestamp.
     *
     * @param taskId the task ID
     * @return true if update was successful, false otherwise
     */
    public boolean markAsProcessing(String taskId) {
        Bson filter = Filters.eq("_id", taskId);
        Bson statusUpdate = Updates.set("status", TaskStatus.PROCESSING.toString());
        Bson startedAtUpdate = Updates.set("startedAt", Instant.now());
        Bson combinedUpdate = Updates.combine(statusUpdate, startedAtUpdate);

        UpdateResult result = tasksCollection.updateOne(filter, combinedUpdate);
        boolean success = result.getModifiedCount() > 0;

        if (success) {
            logger.debug("Marked task {} as PROCESSING", taskId);
        }

        return success;
    }

    /**
     * Update task with error information.
     *
     * @param task the task to update
     * @param errorMessage the error message
     */
    public void markAsFailed(Task task, String errorMessage) {
        Bson filter = Filters.eq("_id", task.getId());
        Bson statusUpdate = Updates.set("status", TaskStatus.FAILED.toString());
        Bson errorUpdate = Updates.set("errorMessage", errorMessage);
        Bson retryUpdate = Updates.inc("retryCount", 1);
        Bson completedAtUpdate = Updates.set("completedAt", Instant.now());
        Bson combinedUpdate = Updates.combine(statusUpdate, errorUpdate, retryUpdate, completedAtUpdate);

        tasksCollection.updateOne(filter, combinedUpdate);
        logger.error("Marked task {} as FAILED: {}", task.getId(), errorMessage);
    }

    /**
     * Find stale tasks that have been in PROCESSING status for too long.
     *
     * @param threshold the time threshold for considering a task stale
     * @return list of stale tasks
     */
    public List<Task> findStaleTasks(Instant threshold) {
        List<Task> tasks = new ArrayList<>();

        Bson statusFilter = Filters.eq("status", TaskStatus.PROCESSING.toString());
        Bson timeFilter = Filters.lt("startedAt", threshold);
        Bson combinedFilter = Filters.and(statusFilter, timeFilter);

        try (MongoCursor<Document> cursor = tasksCollection.find(combinedFilter).iterator()) {
            while (cursor.hasNext()) {
                Document doc = cursor.next();
                Task task = documentToTask(doc);
                tasks.add(task);
            }
        }

        return tasks;
    }

    /**
     * Reset stale tasks back to PENDING status.
     *
     * @param task the stale task to reset
     */
    public void resetStaleTask(Task task) {
        Bson filter = Filters.eq("_id", task.getId());
        Bson statusUpdate = Updates.set("status", TaskStatus.PENDING.toString());
        Bson startedAtUpdate = Updates.unset("startedAt");

        tasksCollection.updateOne(filter, Updates.combine(statusUpdate, startedAtUpdate));
        logger.info("Reset stale task {} to PENDING", task.getId());
    }

    private Task documentToTask(Document doc) {
        try {
            return objectMapper.readValue(doc.toJson(), Task.class);
        } catch (Exception e) {
            logger.error("Error converting document to task: {}", doc.toJson(), e);
            return null;
        }
    }
}
