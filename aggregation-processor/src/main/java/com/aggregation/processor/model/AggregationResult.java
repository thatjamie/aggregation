package com.aggregation.processor.model;

import java.time.Instant;

/**
 * Result of an aggregation execution.
 */
public class AggregationResult {

    private final String taskId;
    private final int processedCount;
    private final Instant completedAt;

    public AggregationResult(String taskId, int processedCount, Instant completedAt) {
        this.taskId = taskId;
        this.processedCount = processedCount;
        this.completedAt = completedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public String toString() {
        return "AggregationResult{" +
                "taskId='" + taskId + '\'' +
                ", processedCount=" + processedCount +
                ", completedAt=" + completedAt +
                '}';
    }
}
