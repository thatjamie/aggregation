plugins {
    application
}

dependencies {
    implementation(project(":aggregation-common"))
    implementation(project(":aggregation-model"))

    // Kafka
    implementation("org.apache.kafka:kafka-clients:${property("kafkaVersion")}")

    // MongoDB
    implementation("org.mongodb:mongodb-driver-sync:${property("mongoVersion")}")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${property("jacksonVersion")}")

    // Configuration
    implementation("org.yaml:snakeyaml:2.2")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.12.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.1")
}

application {
    mainClass.set("com.aggregation.processor.AggregationProcessorApplication")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.aggregation.processor.AggregationProcessorApplication")
    }
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
