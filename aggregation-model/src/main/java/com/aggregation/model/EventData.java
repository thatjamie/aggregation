package com.aggregation.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Generic event data container.
 */
public class EventData {

    @JsonProperty("table")
    private String table;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("record_id")
    private String recordId;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    public EventData() {
    }

    public EventData(String table, String operation, String recordId, Map<String, Object> payload) {
        this.table = table;
        this.operation = operation;
        this.recordId = recordId;
        this.payload = payload;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
