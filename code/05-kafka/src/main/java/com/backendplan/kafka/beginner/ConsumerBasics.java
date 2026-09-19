package com.backendplan.kafka.beginner;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

// The smallest useful Kafka consumer: subscribe, poll, print what comes back.
public class ConsumerBasics {

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "consumer-basics-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("orders-basics"));

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            System.out.println("Polled " + records.count() + " record(s):");
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("  partition=%d offset=%d key=%s value=%s%n",
                        record.partition(), record.offset(), record.key(), record.value());
            }
        }
    }
}
