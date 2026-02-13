#!/bin/bash

# Development startup script

set -e

echo "Starting Aggregation Service development environment..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker first."
    exit 1
fi

# Start infrastructure
echo "Starting infrastructure (Kafka, Redis, PostgreSQL)..."
docker-compose up -d

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
timeout 60 bash -c 'until docker-compose exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 2>/dev/null; do sleep 2; done' || {
    echo "Error: Kafka did not start in time"
    exit 1
}

# Create topics
echo "Creating Kafka topics..."
docker-compose exec -T kafka kafka-topics --create --topic data.events --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092 2>/dev/null || echo "Topic data.events may already exist"
docker-compose exec -T kafka kafka-topics --create --topic data.aggregated --partitions 3 --replication-factor 1 --bootstrap-server localhost:9092 2>/dev/null || echo "Topic data.aggregated may already exist"

echo "Infrastructure started successfully!"
echo ""
echo "Services:"
echo "  - Kafka: localhost:9092"
echo "  - Kafka UI: http://localhost:8090"
echo "  - Redis: localhost:6379"
echo "  - Redis Commander: http://localhost:8081"
echo "  - PostgreSQL: localhost:5432"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin)"
echo ""
echo "To run the application:"
echo "  ./gradlew :aggregation-processor:run"
echo "  ./gradlew :aggregation-api:bootRun"
