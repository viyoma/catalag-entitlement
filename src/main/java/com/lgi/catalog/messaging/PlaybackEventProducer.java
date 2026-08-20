package com.lgi.catalog.messaging;

import com.lgi.catalog.core.EntitlementDecision;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * CURRENT STATE (legacy). Raw Kafka client publishing hand-built JSON strings.
 *
 * AWS Transform target: Amazon MSK, with events wrapped in the CloudEvents
 * envelope (spec-compliant) rather than the ad-hoc JSON below. Serialization
 * moves to a CloudEvents Kafka serializer.
 */
public class PlaybackEventProducer {

    private final KafkaProducer<String, String> producer;
    private final String topic;

    public PlaybackEventProducer(String bootstrapServers, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        props.put("acks", "1");
        this.producer = new KafkaProducer<>(props);
    }

    public void publishAuthorization(EntitlementDecision decision) {
        // Hand-rolled JSON - no schema, no CloudEvents envelope
        String json = String.format(
                "{\"titleId\":\"%s\",\"subscriberId\":\"%s\",\"allowed\":%s,"
                        + "\"reason\":\"%s\",\"country\":\"%s\",\"decidedAt\":\"%s\"}",
                decision.titleId(), decision.subscriberId(), decision.allowed(),
                decision.reason(), decision.country(), decision.decidedAt());

        producer.send(new ProducerRecord<>(topic, decision.subscriberId(), json));
    }

    public void close() {
        producer.close();
    }
}
