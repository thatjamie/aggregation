// MongoDB initialization script for aggregation_db

// Switch to aggregation database
db = db.getSiblingDB('aggregation_db');

print('Initializing aggregation_db...');

// Create aggregation_tasks collection with indexes
db.createCollection('aggregation_tasks');
print('Created collection: aggregation_tasks');

// Create indexes for task collection
db.aggregation_tasks.createIndex(
    { status: 1, scheduledAt: 1 },
    { name: 'status_scheduledAt_idx' }
);
print('Created index: status_scheduledAt_idx');

db.aggregation_tasks.createIndex(
    { taskType: 1 },
    { name: 'taskType_idx' }
);
print('Created index: taskType_idx');

db.aggregation_tasks.createIndex(
    { createdAt: 1 },
    { name: 'createdAt_idx' }
);
print('Created index: createdAt_idx');

// Create a TTL index to automatically clean up old completed tasks (24 hours for POC)
db.aggregation_tasks.createIndex(
    { completedAt: 1 },
    { name: 'completedAt_ttl_idx', expireAfterSeconds: 86400 }
);
print('Created TTL index: completedAt_ttl_idx (24 hours)');

// Insert sample task documents for testing
db.aggregation_tasks.insertMany([
    {
        taskType: 'daily_sales_summary',
        status: 'PENDING',
        createdAt: new Date(),
        scheduledAt: new Date(),
        sourceCollection: 'orders',
        targetCollection: 'daily_summary',
        criteria: { date: { $gte: '2024-01-01' } },
        aggregationConfig: {
            pipeline: [
                { $match: { status: 'completed' } },
                {
                    $group: {
                        _id: '$date',
                        total: { $sum: '$amount' },
                        count: { $sum: 1 }
                    }
                }
            ]
        },
        retryCount: 0
    },
    {
        taskType: 'user_activity_summary',
        status: 'PENDING',
        createdAt: new Date(),
        scheduledAt: new Date(),
        sourceCollection: 'user_events',
        targetCollection: 'user_summary',
        aggregationConfig: {
            pipeline: [
                {
                    $group: {
                        _id: '$userId',
                        eventCount: { $sum: 1 },
                        lastSeen: { $max: '$timestamp' }
                    }
                }
            ]
        },
        retryCount: 0
    }
]);
print('Inserted sample tasks');

// Create sample source collections for testing
db.createCollection('orders');
db.createCollection('user_events');
print('Created sample collections: orders, user_events');

// Insert sample data into orders collection
db.orders.insertMany([
    {
        orderId: 'ORD001',
        userId: 'user1',
        amount: 100,
        status: 'completed',
        date: '2024-01-15',
        createdAt: new Date()
    },
    {
        orderId: 'ORD002',
        userId: 'user2',
        amount: 200,
        status: 'completed',
        date: '2024-01-15',
        createdAt: new Date()
    },
    {
        orderId: 'ORD003',
        userId: 'user1',
        amount: 150,
        status: 'completed',
        date: '2024-01-16',
        createdAt: new Date()
    }
]);
print('Inserted sample data into orders collection');

// Insert sample data into user_events collection
db.user_events.insertMany([
    {
        userId: 'user1',
        action: 'login',
        timestamp: new Date('2024-01-15T10:00:00Z'),
        metadata: { ip: '192.168.1.1', browser: 'chrome' }
    },
    {
        userId: 'user1',
        action: 'purchase',
        timestamp: new Date('2024-01-15T10:05:00Z'),
        metadata: { amount: 100, productId: 'PROD001' }
    },
    {
        userId: 'user2',
        action: 'login',
        timestamp: new Date('2024-01-15T11:00:00Z'),
        metadata: { ip: '192.168.1.2', browser: 'firefox' }
    },
    {
        userId: 'user2',
        action: 'purchase',
        timestamp: new Date('2024-01-15T11:10:00Z'),
        metadata: { amount: 200, productId: 'PROD002' }
    },
    {
        userId: 'user1',
        action: 'logout',
        timestamp: new Date('2024-01-15T12:00:00Z'),
        metadata: { sessionDuration: 7200 }
    }
]);
print('Inserted sample data into user_events collection');

// Create indexes on source collections for better query performance
db.orders.createIndex({ date: 1 });
db.orders.createIndex({ status: 1 });
db.orders.createIndex({ userId: 1 });
print('Created indexes on orders collection');

db.user_events.createIndex({ userId: 1 });
db.user_events.createIndex({ action: 1 });
db.user_events.createIndex({ timestamp: 1 });
print('Created indexes on user_events collection');

print('MongoDB initialization completed successfully!');
print('Collections created:', db.getCollectionNames());
