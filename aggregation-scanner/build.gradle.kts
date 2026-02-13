plugins {
    application
}

dependencies {
    implementation(project(":aggregation-common"))
    implementation(project(":aggregation-model"))

    // Spring Boot for MongoDB and Kafka
    implementation("org.springframework.boot:spring-boot-starter:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-json:3.2.0")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:${property("kafkaVersion")}")

    // Spring scheduler
    implementation("org.springframework:spring-context:6.1.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
}

application {
    mainClass.set("com.aggregation.scanner.TaskScannerApplication")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.aggregation.scanner.TaskScannerApplication")
    }
    from(configurations.runtimeClasspath.get().map { zipTree(it) }) {
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
