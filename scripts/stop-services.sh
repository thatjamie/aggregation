#!/bin/bash
# Stop services for aggregation project

echo "=== Stopping Services ==="

# Stop Kafka
if pgrep -f "kafka.Kafka" > /dev/null; then
    echo "Stopping Kafka..."
    /opt/kafka/bin/kafka-server-stop.sh 2>/dev/null || true
    sleep 3
    pkill -f "kafka.Kafka" 2>/dev/null || true
    echo "✓ Kafka stopped"
else
    echo "! Kafka not running"
fi

# Stop MongoDB
if pgrep -x mongod > /dev/null; then
    echo "Stopping MongoDB..."
    mongosh --quiet --eval "db.adminCommand({ shutdown: 1 })" 2>/dev/null || true
    sleep 2
    echo "✓ MongoDB stopped"
else
    echo "! MongoDB not running"
fi

echo ""
echo "All services stopped."
