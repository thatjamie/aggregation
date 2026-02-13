package com.aggregation.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Task model for aggregation jobs stored in MongoDB.
 * Tasks represent aggregation operations to be executed by the processor.
 */
public class Task {

    @JsonProperty("_id")
    private String id;

    @JsonProperty("task_type")
    private String taskType;

    @JsonProperty("status")
    private TaskStatus status;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonProperty("scheduled_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant scheduledAt;

    @JsonProperty("started_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant startedAt;

    @JsonProperty("completed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant completedAt;

    @JsonProperty("source_collection")
    private String sourceCollection;

    @JsonProperty("target_collection")
    private String targetCollection;

    @JsonProperty("criteria")
    private Map<String, Object> criteria;

    @JsonProperty("aggregation_config")
    private Map<String, Object> aggregationConfig;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("retry_count")
    private int retryCount;

    @JsonProperty("last_processed_id")
    private String lastProcessedId;

    public Task() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.status = TaskStatus.PENDING;
        this.retryCount = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getSourceCollection() {
        return sourceCollection;
    }

    public void setSourceCollection(String sourceCollection) {
        this.sourceCollection = sourceCollection;
    }

    public String getTargetCollection() {
        return targetCollection;
    }

    public void setTargetCollection(String targetCollection) {
        this.targetCollection = targetCollection;
    }

    public Map<String, Object> getCriteria() {
        return criteria;
    }

    public void setCriteria(Map<String, Object> criteria) {
        this.criteria = criteria;
    }

    public Map<String, Object> getAggregationConfig() {
        return aggregationConfig;
    }

    public void setAggregationConfig(Map<String, Object> aggregationConfig) {
        this.aggregationConfig = aggregationConfig;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastProcessedId() {
        return lastProcessedId;
    }

    public void setLastProcessedId(String lastProcessedId) {
        this.lastProcessedId = lastProcessedId;
    }
}
