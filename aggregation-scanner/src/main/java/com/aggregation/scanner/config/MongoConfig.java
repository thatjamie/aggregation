package com.aggregation.scanner.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MongoDB configuration for the task scanner.
 */
public class MongoConfig {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfig.class);

    private final MongoClient mongoClient;
    private final MongoDatabase database;

    public MongoConfig(String uri, String databaseName) {
        logger.info("Connecting to MongoDB: {}", uri);
        this.mongoClient = MongoClients.create(uri);
        this.database = mongoClient.getDatabase(databaseName);
        logger.info("Connected to database: {}", databaseName);
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("MongoDB connection closed");
        }
    }
}
