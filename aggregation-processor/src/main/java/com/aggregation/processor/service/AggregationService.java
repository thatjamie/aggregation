package com.aggregation.processor.service;

import com.aggregation.model.Task;
import com.aggregation.processor.model.AggregationResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.result.InsertManyResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for executing aggregation tasks.
 */
public class AggregationService {

    private static final Logger logger = LoggerFactory.getLogger(AggregationService.class);

    private final MongoDatabase mongoDatabase;

    public AggregationService(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    /**
     * Execute an aggregation task.
     *
     * @param task the task to execute
     * @return aggregation result with processed count
     */
    public AggregationResult executeAggregation(Task task) {
        logger.info("Executing aggregation for task: {}", task.getId());

        try {
            // Build aggregation pipeline
            List<Bson> pipeline = buildAggregationPipeline(task);

            logger.debug("Aggregation pipeline has {} stages", pipeline.size());

            // Get source collection
            MongoCollection<Document> sourceCollection =
                mongoDatabase.getCollection(task.getSourceCollection());

            // Execute aggregation
            List<Document> resultDocs = new ArrayList<>();
            sourceCollection.aggregate(pipeline)
                .allowDiskUse(true)
                .into(resultDocs);

            logger.info("Aggregation produced {} documents", resultDocs.size());

            // Save to target collection if we have results
            if (!resultDocs.isEmpty()) {
                MongoCollection<Document> targetCollection =
                    mongoDatabase.getCollection(task.getTargetCollection());

                // Add metadata to each result document
                for (Document doc : resultDocs) {
                    doc.append("_taskId", task.getId());
                    doc.append("_aggregatedAt", Instant.now());
                }

                InsertManyResult insertResult = targetCollection.insertMany(resultDocs);
                logger.info("Saved {} documents to {}", resultDocs.size(), task.getTargetCollection());
            }

            return new AggregationResult(
                task.getId(),
                resultDocs.size(),
                Instant.now()
            );

        } catch (Exception e) {
            logger.error("Aggregation failed for task {}: {}", task.getId(), e.getMessage(), e);
            throw new RuntimeException("Aggregation execution failed", e);
        }
    }

    /**
     * Build MongoDB aggregation pipeline from task configuration.
     *
     * @param task the task containing aggregation configuration
     * @return list of aggregation stages
     */
    @SuppressWarnings("unchecked")
    private List<Bson> buildAggregationPipeline(Task task) {
        List<Bson> pipeline = new ArrayList<>();

        // Add $match stage if criteria present
        if (task.getCriteria() != null && !task.getCriteria().isEmpty()) {
            Document criteriaDoc = new Document(task.getCriteria());
            pipeline.add(Aggregates.match(criteriaDoc));
            logger.debug("Added $match stage: {}", criteriaDoc.toJson());
        }

        // Add custom pipeline stages from config
        if (task.getAggregationConfig() != null && task.getAggregationConfig().containsKey("pipeline")) {
            List<Map<String, Object>> stages =
                (List<Map<String, Object>>) task.getAggregationConfig().get("pipeline");

            for (Map<String, Object> stage : stages) {
                Bson bsonStage = parseStage(stage);
                if (bsonStage != null) {
                    pipeline.add(bsonStage);
                }
            }
        }

        return pipeline;
    }

    /**
     * Parse a single aggregation stage from configuration.
     *
     * @param stage the stage map
     * @return Bson aggregation stage
     */
    @SuppressWarnings("unchecked")
    private Bson parseStage(Map<String, Object> stage) {
        if (stage.containsKey("$match")) {
            Map<String, Object> matchStage = (Map<String, Object>) stage.get("$match");
            Document matchDoc = new Document(matchStage);
            logger.debug("Adding $match stage: {}", matchDoc.toJson());
            return Aggregates.match(matchDoc);

        } else if (stage.containsKey("$group")) {
            Map<String, Object> groupStage = (Map<String, Object>) stage.get("$group");
            Object idValue = groupStage.get("_id");

            // Build accumulators
            List<BsonField> accumulators = new ArrayList<>();
            for (Map.Entry<String, Object> entry : groupStage.entrySet()) {
                if (!entry.getKey().equals("_id")) {
                    Map<String, Object> acc = (Map<String, Object>) entry.getValue();
                    accumulators.add(parseAccumulator(entry.getKey(), acc));
                }
            }

            Document idDoc = idValue instanceof Map ? new Document((Map<String, Object>) idValue) : new Document("_id", idValue);
            logger.debug("Adding $group stage with _id: {}", idDoc.toJson());
            return Aggregates.group(idDoc, accumulators);

        } else if (stage.containsKey("$sort")) {
            Map<String, Object> sortStage = (Map<String, Object>) stage.get("$sort");
            Document sortDoc = new Document(sortStage);
            logger.debug("Adding $sort stage: {}", sortDoc.toJson());
            return Aggregates.sort(sortDoc);

        } else if (stage.containsKey("$limit")) {
            int limit = ((Number) stage.get("$limit")).intValue();
            logger.debug("Adding $limit stage: {}", limit);
            return Aggregates.limit(limit);

        } else if (stage.containsKey("$skip")) {
            int skip = ((Number) stage.get("$skip")).intValue();
            logger.debug("Adding $skip stage: {}", skip);
            return Aggregates.skip(skip);

        } else if (stage.containsKey("$project")) {
            Map<String, Object> projectStage = (Map<String, Object>) stage.get("$project");
            Document projectDoc = new Document(projectStage);
            logger.debug("Adding $project stage: {}", projectDoc.toJson());
            return Aggregates.project(projectDoc);

        } else if (stage.containsKey("$lookup")) {
            Map<String, Object> lookup = (Map<String, Object>) stage.get("$lookup");
            logger.debug("Adding $lookup stage: from={}", lookup.get("from"));
            return Aggregates.lookup(
                (String) lookup.get("from"),
                (String) lookup.get("localField"),
                (String) lookup.get("foreignField"),
                (String) lookup.get("as")
            );

        } else if (stage.containsKey("$unwind")) {
            String fieldPath = (String) stage.get("$unwind");
            logger.debug("Adding $unwind stage: {}", fieldPath);
            return Aggregates.unwind(fieldPath);
        }

        logger.warn("Unknown aggregation stage: {}", stage.keySet());
        return null;
    }

    /**
     * Parse an accumulator from $group stage.
     *
     * @param fieldName the field name for the accumulator result
     * @param accumulator the accumulator map (e.g., {"$sum": "$amount"})
     * @return BsonField accumulator
     */
    @SuppressWarnings("unchecked")
    private BsonField parseAccumulator(String fieldName, Map<String, Object> accumulator) {
        for (Map.Entry<String, Object> entry : accumulator.entrySet()) {
            String operator = entry.getKey();
            Object value = entry.getValue();

            switch (operator) {
                case "$sum":
                    return Accumulators.sum(fieldName, value.toString());
                case "$avg":
                    return Accumulators.avg(fieldName, value.toString());
                case "$min":
                    return Accumulators.min(fieldName, value.toString());
                case "$max":
                    return Accumulators.max(fieldName, value.toString());
                case "$first":
                    return Accumulators.first(fieldName, value.toString());
                case "$last":
                    return Accumulators.last(fieldName, value.toString());
                case "$count":
                case "$addToSet":
                case "$push":
                    // These require different handling, but for now use sum as default
                    return Accumulators.sum(fieldName, value.toString());
                default:
                    logger.warn("Unknown accumulator operator: {}", operator);
                    return Accumulators.sum(fieldName, value.toString());
            }
        }

        return Accumulators.sum(fieldName, 0);
    }
}
