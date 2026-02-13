package com.aggregation.model;

/**
 * Status enum for aggregation tasks.
 */
public enum TaskStatus {
    /**
     * Task is waiting to be processed.
     */
    PENDING,

    /**
     * Task is currently being processed.
     */
    PROCESSING,

    /**
     * Task completed successfully.
     */
    COMPLETED,

    /**
     * Task failed during processing.
     */
    FAILED,

    /**
     * Task was cancelled before or during processing.
     */
    CANCELLED
}
