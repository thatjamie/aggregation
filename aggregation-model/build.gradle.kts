dependencies {
    implementation(project(":aggregation-common"))

    // MongoDB
    implementation("org.mongodb:mongodb-driver-sync:${property("mongoVersion")}")

    // Kafka (for serializers used by API and other modules)
    implementation("org.apache.kafka:kafka-clients:${property("kafkaVersion")}")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${property("jacksonVersion")}")
}
