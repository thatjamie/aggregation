package com.aggregation.processor;

import com.aggregation.processor.consumer.TaskConsumer;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application class for the task-based aggregation processor.
 */
public class AggregationProcessorApplication {

    private static final Logger logger = LoggerFactory.getLogger(AggregationProcessorApplication.class);

    private final TaskConsumer taskConsumer;
    private final PrometheusMeterRegistry meterRegistry;

    public AggregationProcessorApplication() {
        // Initialize metrics
        this.meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        // Bind JVM metrics
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        new ProcessorMetrics().bindTo(meterRegistry);

        // Add to global registry
        Metrics.addRegistry(meterRegistry);

        // Initialize task consumer
        this.taskConsumer = new TaskConsumer();

        logger.info("AggregationProcessorApplication initialized");
    }

    /**
     * Start the processor.
     */
    public void start() {
        logger.info("Starting Aggregation Processor...");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Aggregation Processor...");
        }));

        // Start consuming tasks
        taskConsumer.start();
    }

    /**
     * Get Prometheus scrape endpoint.
     */
    public String getPrometheusScrapeData() {
        if (meterRegistry != null) {
            return meterRegistry.scrape();
        }
        return "";
    }

    public static void main(String[] args) {
        AggregationProcessorApplication app = new AggregationProcessorApplication();
        app.start();
    }
}
