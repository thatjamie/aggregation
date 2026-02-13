plugins {
    application
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
}

dependencies {
    implementation(project(":aggregation-common"))
    implementation(project(":aggregation-model"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Redis (for fast lookups)
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Prometheus metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}

application {
    mainClass.set("com.aggregation.api.ApiApplication")
}

tasks.bootJar {
    archiveFileName.set("aggregation-api.jar")
}
