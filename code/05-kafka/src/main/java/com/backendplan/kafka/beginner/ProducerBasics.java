package com.backendplan.kafka.beginner;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

// The smallest useful Kafka producer: connect, send a few records, read back where each landed.
public class ProducerBasics {

    public static void main(String[] args) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= 5; i++) {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>("orders-basics", "order-" + i, "payload-for-order-" + i);
                RecordMetadata metadata = producer.send(record).get();
                System.out.printf("Sent order-%d -> partition %d, offset %d%n",
                        i, metadata.partition(), metadata.offset());
            }
        }
    }
}
