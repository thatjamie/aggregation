# Aggregation Service

A production-ready **task-based data aggregation system** built with Java 21, designed to handle large-volume batch aggregation jobs. Uses MongoDB for task storage, Kafka for task distribution, and a scanner/processor architecture for scalable aggregation.

## Overview

This project demonstrates a modern architecture for aggregating data using a task-based scanning pattern where:
1. Tasks are stored in MongoDB with configurable schedules and criteria
2. A scanner service polls for pending tasks and publishes them to Kafka
3. Processor instances consume tasks and execute MongoDB aggregation pipelines
4. Results are written to target collections for downstream consumption

```
┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   MongoDB    │────▶│   Task      │────▶│  Aggregation  │
│   Tasks      │     │   Scanner   │     │  Consumer     │
│   Collection  │     │  Service    │     │ (Processor)   │
└──────────────┘     └──────┬──────┘     └──────┬───────┘
         ▲                     │                      │
         │                     ▼                      ▼
         │              ┌─────────────┐     ┌──────────────┐
         │              │    Kafka    │     │   MongoDB    │
         │              │   Topics    │────▶│   Results    │
         │              └─────────────┘     └──────────────┘
         │
         └───────── Status Updates (PENDING → PROCESSING → COMPLETED)
```

## Features

| Feature | Technology |
|---------|------------|
| **Task Queue** | MongoDB with indexed collections |
| **Task Distribution** | Kafka with consumer groups |
| **Aggregation Engine** | MongoDB Aggregation Pipeline |
| **REST API** | Spring Boot 3.x |
| **Metrics** | Micrometer + Prometheus |
| **Monitoring** | Grafana dashboards included |
| **Scanner** | Scheduled polling with configurable intervals |
| **Processor** | Multi-instance consumer for parallel processing |

## Quick Start

### Prerequisites
- Java 21+
- A container runtime (see options below)
- Gradle 8.x (or use provided wrapper)

### Container Runtime Options (macOS)

Choose one of the following container runtimes:

#### Option 1: Docker Desktop (Traditional)
```bash
# Install Docker Desktop from docker.com
# Then use docker-compose
docker-compose up -d
```

#### Option 2: Colima (Recommended for Apple Silicon)
Lightweight Docker-compatible runtime optimized for macOS:
```bash
# Install via Homebrew
brew install colima docker docker-compose

# Start Colima
colima start

# Use docker-compose as usual
docker-compose up -d
```

#### Option 3: Podman + Podman Compose
Red Hat's daemonless container engine:
```bash
# Install via Homebrew
brew install podman podman-compose

# Start services
podman-compose up -d
```

#### Option 4: Apple Container CLI (Experimental)
Apple's native container runtime for macOS (limited compose support):
```bash
# Requires macOS 15+ and Xcode 26 beta
# Note: No native compose support yet - must run containers individually

# Start MongoDB
container run \
  --name mongodb \
  --publish 27017:27017 \
  --volume mongodb_data:/data/db \
  --env MONGO_INITDB_DATABASE=aggregation_db \
  mongo:7.0

# Start Kafka (KRaft mode)
container run \
  --name kafka \
  --publish 9092:9092 \
  --publish 9101:9101 \
  --env KAFKA_NODE_ID=1 \
  --env KAFKA_PROCESS_ROLES=broker,controller \
  --env KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,BROKER://0.0.0.0:29093 \
  --env KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  --env KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:29093 \
  confluentinc/cp-kafka:latest

# Start Redis (optional)
container run \
  --name redis \
  --publish 6379:6379 \
  redis:7-alpine
```

**Recommendation:** For multi-service projects like this, **Colima (Option 2)** provides the best balance of:
- Full docker-compose compatibility
- Apple Silicon optimization
- Lightweight resource usage

| Runtime | Compose Support | Apple Silicon | Resource Usage |
|---------|-----------------|---------------|----------------|
| Docker Desktop | ✅ Full | ✅ Good | High |
| Colima | ✅ Full | ✅ Excellent | Low |
| Podman | ✅ Via podman-compose | ✅ Good | Low |
| Apple Container | ❌ None | ✅ Native | Very Low |

### 1. Start Infrastructure (2 min)

```bash
# Start MongoDB, Kafka, Prometheus, Grafana
docker-compose up -d

# Verify everything is running
docker-compose ps
```

**Services exposed:**
| Service | URL | Credentials |
|---------|-----|-------------|
| Kafka UI | http://localhost:8090 | - |
| Redis Commander | http://localhost:8081 | - |
| Grafana | http://localhost:3000 | admin/admin |
| Prometheus | http://localhost:9090 | - |
| MongoDB | mongodb://localhost:27017 | - |

### 2. Build & Run

```bash
# Build all modules
./gradlew build

# Run the scanner service (Terminal 1)
./gradlew :aggregation-scanner:run

# Run the processor service (Terminal 2)
./gradlew :aggregation-processor:run

# Run the REST API (Terminal 3 - optional)
./gradlew :aggregation-api:bootRun
```

### 3. Test the System

```bash
# Connect to MongoDB
mongosh mongodb://localhost:27017/aggregation_db

# Check existing tasks
db.aggregation_tasks.find().pretty()

# Insert a new aggregation task
db.aggregation_tasks.insertOne({
    taskType: "daily_summary",
    status: "PENDING",
    createdAt: new Date(),
    scheduledAt: new Date(),
    sourceCollection: "orders",
    targetCollection: "daily_summary",
    aggregationConfig: {
        pipeline: [
            { $match: { status: "completed" } },
            { $group: { _id: "$date", total: { $sum: "$amount" } } }
        ]
    }
})

# Watch task status change
# Scanner will pick it up → Processor will execute → Results in target collection
```

## Project Structure

```
aggregation/
├── aggregation-api/          # Spring Boot REST API
│   └── src/main/java/com/aggregation/api/
│       ├── ApiApplication.java
│       ├── controller/       # REST endpoints
│       ├── service/          # Business logic
│       └── config/          # MongoDB, Jackson
│
├── aggregation-processor/    # Task Consumer application
│   └── src/main/java/com/aggregation/processor/
│       ├── AggregationProcessorApplication.java
│       ├── consumer/        # Kafka consumer logic
│       ├── service/         # Aggregation execution
│       └── model/          # Result models
│
├── aggregation-scanner/     # Task Scanner service
│   └── src/main/java/com/aggregation/scanner/
│       ├── TaskScannerApplication.java
│       ├── service/         # Scheduled scanning logic
│       ├── producer/        # Kafka publisher
│       ├── repository/      # MongoDB task repository
│       └── config/         # MongoDB configuration
│
├── aggregation-model/        # Domain models
│   └── src/main/java/com/aggregation/model/
│       ├── Task.java
│       ├── TaskStatus.java
│       ├── Event.java
│       ├── AggregatedData.java
│       └── serializer/       # JSON serdes for Kafka
│
├── aggregation-common/       # Shared utilities
│
├── docker-compose.yml        # Infrastructure
├── docker/                  # Config files
│   ├── mongo-init.js       # MongoDB initialization
│   └── prometheus.yml     # Metrics configuration
└── scripts/                 # Helper scripts
```

## Configuration

### Key Configuration Files

| File | Purpose |
|------|---------|
| `aggregation-scanner/src/main/resources/application.yml` | Scanner schedule & MongoDB config |
| `aggregation-processor/src/main/resources/application.yml` | Consumer & aggregation config |
| `aggregation-api/src/main/resources/application.yml` | API & MongoDB config |

### Scanner Configuration

Edit `aggregation-scanner/src/main/resources/application.yml`:

```yaml
scanner:
  interval-ms: 60000          # Scan every 60 seconds
  batch-size: 100             # Process up to 100 tasks per scan

kafka:
  bootstrap-servers: localhost:9092
  topic:
    tasks: aggregation.tasks   # Topic to publish tasks

mongo:
  uri: mongodb://localhost:27017
  database: aggregation_db
```

### Processor Configuration

Edit `aggregation-processor/src/main/resources/application.yml`:

```yaml
processor:
  concurrency: 3              # Number of consumer threads

kafka:
  consumer:
    group-id: aggregation-processor
    poll-timeout-ms: 1000

mongo:
  uri: mongodb://localhost:27017
  database: aggregation_db
```

## Task Management

### Creating Tasks via MongoDB

```javascript
// Simple aggregation
db.aggregation_tasks.insertOne({
    taskType: "user_activity_summary",
    status: "PENDING",
    scheduledAt: new Date(),
    sourceCollection: "user_events",
    targetCollection: "user_activity_summary",
    aggregationConfig: {
        pipeline: [
            { $group: {
                _id: "$userId",
                eventCount: { $sum: 1 },
                lastSeen: { $max: "$timestamp" }
            }}
        ]
    }
})

// Complex aggregation with multiple stages
db.aggregation_tasks.insertOne({
    taskType: "sales_analysis",
    status: "PENDING",
    scheduledAt: new Date(),
    sourceCollection: "orders",
    targetCollection: "sales_analysis",
    criteria: { date: { $gte: "2024-01-01" } },
    aggregationConfig: {
        pipeline: [
            { $match: { status: "completed" } },
            { $lookup: {
                from: "customers",
                localField: "customerId",
                foreignField: "_id",
                as: "customer"
            }},
            { $unwind: "$customer" },
            { $group: {
                _id: "$customer.region",
                totalSales: { $sum: "$amount" },
                avgOrderValue: { $avg: "$amount" },
                orderCount: { $sum: 1 }
            }},
            { $sort: { totalSales: -1 } },
            { $limit: 10 }
        ]
    }
})

// Scheduled task (run at specific time)
db.aggregation_tasks.insertOne({
    taskType: "nightly_report",
    status: "PENDING",
    scheduledAt: ISODate("2024-01-16T02:00:00Z"),  // Runs at 2 AM
    sourceCollection: "transactions",
    targetCollection: "nightly_reports"
})
```

### Supported Aggregation Stages

The processor supports all MongoDB aggregation stages:

| Stage | Description | Example |
|-------|-------------|----------|
| `$match` | Filter documents | `{ $match: { status: "active" } }` |
| `$group` | Group by field | `{ $group: { _id: "$userId", total: { $sum: "$amount" } } }` |
| `$sort` | Sort results | `{ $sort: { createdAt: -1 } }` |
| `$limit` | Limit results | `{ $limit: 100 }` |
| `$skip` | Skip documents | `{ $skip: 10 }` |
| `$project` | Reshape documents | `{ $project: { userId: 1, total: 1 } }` |
| `$lookup` | Join collections | `{ $lookup: { from: "users", localField: "userId", foreignField: "_id", as: "user" } }` |
| `$unwind` | Flatten arrays | `{ $unwind: "$user" }` |

## API Endpoints

```
# Task Management (when API module is running)
POST /api/v1/tasks
    → Create new aggregation task

GET /api/v1/tasks/{id}
    → Get task status and details

GET /api/v1/tasks
    → List all tasks with optional filtering

PUT /api/v1/tasks/{id}/cancel
    → Cancel a pending or processing task

# Aggregated Data
GET /api/v1/aggregations/{key}
    → Get aggregated data by key

GET /api/v1/aggregations/health
    → Health check

# Metrics
GET /actuator/prometheus
    → Prometheus metrics
```

## Task Lifecycle

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  PENDING  │───▶│ SCANNING │───▶│PROCESSING│───▶│ COMPLETED │
└──────────┘    └──────────┘    └────┬─────┘    └──────────┘
                                            │
                                            ▼
                                     ┌──────────┐
                                     │  FAILED   │
                                     └────┬─────┘
                                          │
                                          ▼ (retry)
                                     ┌──────────┐
                                     │  PENDING  │
                                     └──────────┘
```

**Task Status Values:**
- `PENDING` - Task is queued, waiting to be processed
- `PROCESSING` - Task is currently being executed
- `COMPLETED` - Task finished successfully
- `FAILED` - Task failed (check `errorMessage` field)
- `CANCELLED` - Task was cancelled before/during processing

## Data Formats

### Task Document
```json
{
  "_id": "550e8400-e29b-41d4-a716-446655440000",
  "taskType": "daily_sales_summary",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "scheduledAt": "2024-01-15T12:00:00Z",
  "startedAt": null,
  "completedAt": null,
  "sourceCollection": "orders",
  "targetCollection": "daily_summary",
  "criteria": {
    "date": { "$gte": "2024-01-01" }
  },
  "aggregationConfig": {
    "pipeline": [
      { "$match": { "status": "completed" } },
      { "$group": {
        "_id": "$date",
        "total": { "$sum": "$amount" }
      }}
    ]
  },
  "errorMessage": null,
  "retryCount": 0,
  "lastProcessedId": null
}
```

### Aggregation Result (stored in target collection)
```json
{
  "_id": "2024-01-15",
  "total": 5420.50,
  "count": 125,
  "_taskId": "550e8400-e29b-41d4-a716-446655440000",
  "_aggregatedAt": "2024-01-15T14:30:00Z"
}
```

## Testing

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :aggregation-processor:test
./gradlew :aggregation-scanner:test
./gradlew :aggregation-api:test
```

## Development Scripts

```bash
# Start all infrastructure
./scripts/start-dev.sh

# Stop infrastructure
./scripts/stop-dev.sh

# Clean build
./gradlew clean build
```

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21 |
| MongoDB | 7.0 |
| Kafka | 3.7.0 (KRaft mode) |
| MongoDB Driver | 5.1.4 |
| Jackson | 2.16.1 |
| Gradle | 8.5 |

## Production Checklist

- [ ] Configure multiple Kafka partitions for parallel processing
- [ ] Enable authentication for MongoDB and Kafka
- [ ] Set up proper monitoring & alerting (Prometheus/Grafana)
- [ ] Implement graceful shutdown handlers
- [ ] Configure backup strategy for MongoDB collections
- [ ] Set up log aggregation (ELK, Loki, etc.)
- [ ] Configure TTL indexes for automatic cleanup of old tasks
- [ ] Implement dead letter queue for failed tasks
- [ ] Add rate limiting for API endpoints
- [ ] Configure replica sets for MongoDB high availability

## Performance Considerations

| Factor | Recommendation |
|--------|----------------|
| **Scanner Frequency** | Adjust `interval-ms` based on task volume (60s default) |
| **Batch Size** | Increase `batch-size` for high task volumes |
| **Consumer Concurrency** | Match to CPU cores (default: 3) |
| **Kafka Partitions** | Use 3-6 partitions for `aggregation.tasks` topic |
| **MongoDB Indexes** | Ensure indexes on `status`, `scheduledAt`, `taskType` |
| **Aggregation Memory** | Use `allowDiskUse(true)` for large result sets |

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Scanner not picking up tasks | Check MongoDB indexes exist: `db.aggregation_tasks.getIndexes()` |
| Processor crashes | Check logs, verify aggregation pipeline syntax |
| Kafka connection refused | Verify Kafka is running: `docker-compose ps kafka` |
| Tasks stuck in PROCESSING | Check for stale tasks (scanner auto-resets after 1 hour) |
| Out of memory | Increase heap: `AGGREGATION_OPTS="-Xmx4g"` |
| Empty results | Verify source collection has data matching criteria |

## Reactive vs Blocking Implementation

### Current Approach: Blocking/Synchronous

The current implementation uses the **synchronous MongoDB Java driver** with blocking operations:

| Component | Current Implementation |
|-----------|----------------------|
| **MongoDB Driver** | `mongodb-driver-sync` (blocking) |
| **Collection Type** | `MongoCollection<Document>` |
| **Aggregation** | `.aggregate(pipeline).into(List)` - loads all results into memory |
| **Kafka Consumer** | `KafkaConsumer` (poll-based, blocking) |
| **Result Handling** | `List<Document>` - all documents in memory |

**Characteristics:**
- ✅ Simpler to understand and debug
- ✅ Easier to write unit tests
- ✅ Predictable execution flow
- ⚠️ Loads entire result set into memory
- ⚠️ Blocks thread during I/O operations
- ⚠️ May cause OOM with very large aggregation results

### Alternative: Reactive/Streaming

A **reactive implementation** would use async non-blocking operations:

| Component | Reactive Implementation |
|-----------|----------------------|
| **MongoDB Driver** | `mongodb-driver-reactivestreams` |
| **Collection Type** | `MonoMongoCollection<Document>` |
| **Aggregation** | `.aggregate(pipeline)` - returns `Publisher<Document>` |
| **Kafka Consumer** | `ReactiveKafkaConsumer` or `Flux`-based |
| **Result Handling** | `Flux<Document>` - stream processing |

**Benefits of Reactive Approach:**
- ✅ Stream results incrementally (lower memory footprint)
- ✅ Better resource utilization (non-blocking I/O)
- ✅ Natural backpressure support
- ✅ Can handle very large result sets without OOM
- ✅ Better throughput with high concurrency

**Trade-offs:**
- ⚠️ More complex error handling
- ⚠️ Reactive debugging can be challenging
- ⚠️ Requires reactive programming knowledge (Project Reactor/RxJava)
- ⚠️ Steeper learning curve for new developers

### When to Consider Migrating to Reactive

**Stay with blocking if:**
- Result sets are typically small (< 10,000 documents)
- Team is not familiar with reactive programming
- Simplicity and maintainability are priorities
- Low to moderate throughput requirements

**Consider reactive migration if:**
- Aggregation results frequently exceed 100,000+ documents
- Experiencing OOM issues with large aggregations
- Need higher throughput with limited resources
- Team has reactive programming experience
- Want to leverage backpressure for flow control

### Migration Path

To migrate to reactive, would need to:

1. **Update dependencies:**
   ```kotlin
   // build.gradle.kts
   implementation("org.mongodb:mongodb-driver-reactivestreams:${property("mongoVersion")}")
   implementation("io.projectreactor:reactor-core:3.6.0")
   ```

2. **Refactor AggregationService:**
   ```java
   // Current: Blocking
   List<Document> results = new ArrayList<>();
   collection.aggregate(pipeline).into(results);

   // Reactive: Streaming
   Publisher<Document> publisher = collection.aggregate(pipeline);
   Flux.from(publisher)
       .buffer(1000)  // Process in batches
       .flatMap(this::saveBatch)
       .subscribe();
   ```

3. **Update Kafka consumer to reactive:**
   ```java
   // Current: Polling
   ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

   // Reactive: Stream
   Flux<ConsumerRecord<String, String>> flux = KafkaReceiver.create(options)
       .receive();
   ```

## Architecture Migration Notes

This is a **task-based redesign** of the original CDC/Kafka Streams architecture:

### What Changed
- **Removed**: Debezium CDC (no longer captures database changes)
- **Removed**: Kafka Streams (replaced with simple Kafka Consumer/Producer)
- **Removed**: PostgreSQL (replaced with MongoDB as task store)
- **Added**: Task Scanner service for scheduled polling
- **Added**: MongoDB aggregation pipeline execution
- **Added**: Task status lifecycle management

### Benefits of New Architecture
1. **On-demand aggregation** - Execute aggregations when needed, not continuously
2. **Complex pipelines** - Full MongoDB aggregation pipeline support
3. **Scheduled tasks** - Run aggregations at specific times
4. **Better scaling** - Horizontal scaling via consumer groups
5. **Simpler operations** - No CDC connector management

## Resources

- [MongoDB Aggregation Pipeline](https://www.mongodb.com/docs/manual/core/aggregation-pipeline/)
- [Kafka Consumer Documentation](https://kafka.apache.org/documentation/#consumerapi)
- [MongoDB Java Driver](https://mongodb.github.io/mongo-java-driver/)

## License

MIT License - feel free to use this project for learning or production.
