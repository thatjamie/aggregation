package com.aggregation.model.serializer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class JsonSerde<T> implements Serde<T> {

    private final Serde<T> inner;

    public JsonSerde(Class<T> targetType) {
        this.inner = Serdes.serdeFrom(
            new JsonSerializer<>(),
            new JsonDeserializer<>(targetType)
        );
    }

    @Override
    public Serializer<T> serializer() {
        return inner.serializer();
    }

    @Override
    public Deserializer<T> deserializer() {
        return inner.deserializer();
    }
}
