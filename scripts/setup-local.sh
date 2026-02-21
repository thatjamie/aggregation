#!/bin/bash
# Setup script for aggregation project - local install (no Docker)
# Installs: MongoDB 7.0 Community, Apache Kafka 3.7.x (KRaft mode)
#
# Usage: ./scripts/setup-local.sh

set -e

# Non-interactive mode for apt
export DEBIAN_FRONTEND=noninteractive

echo "=== Aggregation Project Setup ==="
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

success() { echo -e "${GREEN}✓${NC} $1"; }
warn() { echo -e "${YELLOW}!${NC} $1"; }
fail() { echo -e "${RED}✗${NC} $1"; exit 1; }

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# ============================================
# CHECK IF ALREADY RUNNING
# ============================================
check_existing() {
    echo "--- Checking existing services ---"
    
    MONGO_RUNNING=false
    KAFKA_RUNNING=false
    
    if pgrep -x mongod > /dev/null; then
        MONGO_RUNNING=true
        success "MongoDB already running"
    fi
    
    if pgrep -f "kafka.Kafka" > /dev/null; then
        KAFKA_RUNNING=true
        success "Kafka already running"
    fi
}

# ============================================
# MONGODB INSTALLATION
# ============================================
install_mongodb() {
    echo ""
    echo "--- Installing MongoDB 7.0 ---"
    
    # Install prerequisites
    apt-get update
    apt-get install -y gnupg curl
    
    # Add MongoDB GPG key
    curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | \
        gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg 2>/dev/null || true
    
    # Add MongoDB repository
    echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] http://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" \
        | tee /etc/apt/sources.list.d/mongodb-org-7.0.list
    
    # Install MongoDB
    apt-get update
    apt-get install -y mongodb-org
    
    success "MongoDB 7.0 installed"
}

start_mongodb() {
    echo ""
    echo "--- Starting MongoDB ---"
    
    # Create data directory
    mkdir -p /data/db
    chown -R mongodb:mongodb /data/db
    
    # Start MongoDB (non-systemd)
    if ! pgrep -x mongod > /dev/null; then
        mkdir -p /var/log/mongodb
        chown mongodb:mongodb /var/log/mongodb
        su mongodb -s /bin/bash -c "mongod --dbpath /data/db --fork --logpath /var/log/mongodb/mongod.log"
        sleep 2
    fi
    
    # Verify
    if mongosh mongodb://localhost:27017 --quiet --eval "db.stats()" > /dev/null 2>&1; then
        success "MongoDB running on port 27017"
    else
        fail "MongoDB failed to start"
    fi
}

# ============================================
# APACHE KAFKA INSTALLATION (KRaft mode)
# ============================================
install_kafka() {
    echo ""
    echo "--- Installing Apache Kafka ---"
    
    # Install Java if needed
    if ! command -v java &> /dev/null; then
        echo "Installing Java 21..."
        apt-get install -y openjdk-21-jre-headless
    fi
    
    # Download Kafka (use latest 3.7.x)
    KAFKA_VERSION="3.7.2"
    KAFKA_DIR="/opt/kafka_${KAFKA_VERSION}"
    
    if [ ! -d "$KAFKA_DIR" ]; then
        echo "Downloading Kafka ${KAFKA_VERSION}..."
        cd /opt
        curl -L -o kafka.tgz "https://dlcdn.apache.org/kafka/${KAFKA_VERSION}/kafka_2.13-${KAFKA_VERSION}.tgz"
        tar -xzf kafka.tgz
        rm kafka.tgz
        ln -sf "$KAFKA_DIR" /opt/kafka
    fi
    
    success "Kafka ${KAFKA_VERSION} installed"
}

configure_kafka() {
    echo ""
    echo "--- Configuring Kafka (KRaft mode) ---"
    
    mkdir -p /tmp/kraft-combined-logs
    
    # Generate cluster ID if not exists
    if [ ! -f /tmp/kafka-cluster-id ]; then
        /opt/kafka/bin/kafka-storage.sh random-uuid > /tmp/kafka-cluster-id
    fi
    KAFKA_CLUSTER_ID=$(cat /tmp/kafka-cluster-id)
    
    # Create server.properties for KRaft mode
    cat > /opt/kafka/config/kraft/server.properties << 'EOF'
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:9093
controller.listener.names=CONTROLLER
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
inter.broker.listener.name=PLAINTEXT
advertised.listeners=PLAINTEXT://localhost:9092
log.dirs=/tmp/kraft-combined-logs
num.partitions=3
default.replication.factor=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
EOF
    
    # Format storage if needed
    if [ ! -f /tmp/kraft-combined-logs/meta.properties ]; then
        /opt/kafka/bin/kafka-storage.sh format -t "$KAFKA_CLUSTER_ID" -c /opt/kafka/config/kraft/server.properties
    fi
    
    success "Kafka configured"
}

start_kafka() {
    echo ""
    echo "--- Starting Kafka ---"
    
    if ! pgrep -f "kafka.Kafka" > /dev/null; then
        /opt/kafka/bin/kafka-server-start.sh -daemon /opt/kafka/config/kraft/server.properties
        sleep 5
    fi
    
    # Verify
    if /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1; then
        success "Kafka running on port 9092"
    else
        fail "Kafka failed to start"
    fi
}

# ============================================
# INITIALIZE DATABASE & TOPICS
# ============================================
init_mongodb() {
    echo ""
    echo "--- Initializing MongoDB Database ---"
    
    if [ -f "$PROJECT_ROOT/docker/mongo-init.js" ]; then
        mongosh mongodb://localhost:27017 < "$PROJECT_ROOT/docker/mongo-init.js"
        success "Database initialized with collections and sample data"
    else
        warn "mongo-init.js not found, skipping"
    fi
}

create_topic() {
    echo ""
    echo "--- Creating Kafka Topic ---"
    
    # Create topic (ignore if exists)
    /opt/kafka/bin/kafka-topics.sh --create --topic aggregation.tasks \
        --bootstrap-server localhost:9092 \
        --partitions 3 --replication-factor 1 2>/dev/null || true
    
    success "Topic 'aggregation.tasks' created"
}

# ============================================
# VERIFY
# ============================================
verify() {
    echo ""
    echo "=== Verification ==="
    
    echo "MongoDB:"
    TASK_COUNT=$(mongosh mongodb://localhost:27017/aggregation_db --quiet --eval "db.aggregation_tasks.find().count()")
    echo "  - Tasks in collection: $TASK_COUNT"
    
    echo "Kafka:"
    /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092 | sed 's/^/  - /'
    
    echo ""
    success "Setup complete!"
}

# ============================================
# MAIN
# ============================================
main() {
    # Check for root
    if [ "$EUID" -ne 0 ]; then
        fail "Please run as root"
    fi
    
    check_existing
    
    # Install if needed
    if ! command -v mongod &> /dev/null; then
        install_mongodb
    fi
    
    if [ ! -d /opt/kafka ]; then
        install_kafka
        configure_kafka
    fi
    
    # Start services
    start_mongodb
    start_kafka
    
    # Initialize
    init_mongodb
    create_topic
    
    verify
    
    echo ""
    echo "=== Services ==="
    echo "MongoDB:  mongodb://localhost:27017"
    echo "Kafka:    localhost:9092"
    echo ""
    echo "=== Run the application ==="
    echo "cd /workspace/aggregation"
    echo "./gradlew build"
    echo "./gradlew :aggregation-scanner:run    # Terminal 1"
    echo "./gradlew :aggregation-processor:run  # Terminal 2"
}

main "$@"
