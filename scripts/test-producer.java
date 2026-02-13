import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;

/**
 * Simple test producer to generate sample events.
 */
public class TestProducer {

    private static final String TOPIC = "data.events";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);

        String[] users = {"alice", "bob", "charlie", "diana", "eve"};
        String[] eventTypes = {"login", "purchase", "view", "logout"};

        System.out.println("Starting to produce events to " + TOPIC);
        System.out.println("Press Ctrl+C to stop...");

        try {
            while (true) {
                String user = users[random.nextInt(users.length)];
                String eventType = eventTypes[random.nextInt(eventTypes.length)];

                Map<String, Object> event = new HashMap<>();
                event.put("event_id", UUID.randomUUID().toString());
                event.put("event_type", eventType);
                event.put("source", "test-producer");
                event.put("aggregate_key", "user:" + user);
                event.put("timestamp", Instant.now().toString());

                Map<String, Object> data = new HashMap<>();
                data.put("operation", "create");
                data.put("record_id", UUID.randomUUID().toString());

                Map<String, Object> payload = new HashMap<>();
                payload.put("user_id", user);
                payload.put("value", random.nextDouble() * 100);
                payload.put("count", random.nextInt(100));
                data.put("payload", payload);
                event.put("data", data);

                String json = mapper.writeValueAsString(event);

                ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC,
                    "user:" + user,
                    json
                );

                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Error sending record: " + exception.getMessage());
                    } else {
                        System.out.println("Sent event: " + eventType + " for " + user);
                    }
                });

                Thread.sleep(1000 + random.nextInt(2000));
            }
        } finally {
            producer.close();
        }
    }
}
