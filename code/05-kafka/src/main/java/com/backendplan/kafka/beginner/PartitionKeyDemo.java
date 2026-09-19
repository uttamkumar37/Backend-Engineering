package com.backendplan.kafka.beginner;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;

// The same key ALWAYS routes to the same partition (the key is hashed to pick one) -
// this is what gives per-entity ordering. Different keys can land anywhere.
public class PartitionKeyDemo {

    public static void main(String[] args) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", "localhost:9092");
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.createTopics(List.of(new NewTopic("partition-key-demo", 4, (short) 1))).all().get();
        } catch (Exception ignored) {
            // topic may already exist from a previous run
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            System.out.println("--- Same key 'order-42', sent 5 times ---");
            for (int i = 1; i <= 5; i++) {
                RecordMetadata metadata = producer.send(
                        new ProducerRecord<>("partition-key-demo", "order-42", "event-" + i)).get();
                System.out.println("  event-" + i + " -> partition " + metadata.partition());
            }

            System.out.println("--- Different keys, one message each ---");
            for (String key : List.of("order-1", "order-2", "order-3", "order-4", "order-5")) {
                RecordMetadata metadata = producer.send(
                        new ProducerRecord<>("partition-key-demo", key, "event-for-" + key)).get();
                System.out.println("  " + key + " -> partition " + metadata.partition());
            }
        }
    }
}
