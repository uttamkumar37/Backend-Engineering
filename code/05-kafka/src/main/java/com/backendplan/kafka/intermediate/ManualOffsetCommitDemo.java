package com.backendplan.kafka.intermediate;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// Manual commit AFTER successful processing gives at-least-once: a crash between processing
// and committing means the next consumer instance re-reads from the last committed offset and
// reprocesses - no message is lost, but duplicates are possible. This is why consumers must be
// idempotent, not a guarantee Kafka gives you for free.
//
// Also demonstrates a real footgun: plain commitSync() with no arguments commits the consumer's
// CURRENT POSITION, which already advances to the end of whatever batch poll() returned - not
// "the record my loop just finished". Committing a specific record's progress requires the
// commitSync(Map<TopicPartition, OffsetAndMetadata>) overload, used below.
public class ManualOffsetCommitDemo {

    private static final String TOPIC = "offset-commit-demo";
    private static final String GROUP = "offset-commit-demo-group";

    static void produceMessages() throws Exception {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", "localhost:9092");
        try (AdminClient admin = AdminClient.create(adminProps)) {
            try {
                admin.deleteConsumerGroups(List.of(GROUP)).all().get();
            } catch (Exception ignored) {} // committed offsets for a group outlive a deleted topic
            try {
                admin.deleteTopics(List.of(TOPIC)).all().get();
                Thread.sleep(1000);
            } catch (Exception ignored) {}
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        }

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i = 1; i <= 5; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "key-" + i, "message-" + i)).get();
            }
        }
    }

    static KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // fetch one record at a time so each poll() maps to exactly one processing step below,
        // making the "crash before committing record 3" scenario deterministic to reproduce
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));
        return consumer;
    }

    static void commitRecord(KafkaConsumer<String, String> consumer, ConsumerRecord<String, String> record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)));
    }

    public static void main(String[] args) throws Exception {
        produceMessages();

        System.out.println("--- First consumer instance: crashes after processing message 3, BEFORE committing ---");
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            int processed = 0;
            while (processed < 3) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
                for (ConsumerRecord<String, String> record : records) {
                    processed++;
                    System.out.println("  processing offset " + record.offset() + ": " + record.value());
                    if (processed == 3) {
                        System.out.println("  (simulated crash here - offset for message 3 is NOT committed)");
                        break;
                    }
                    commitRecord(consumer, record); // commits ONLY this record's offset, not the whole batch
                }
            }
        }

        Thread.sleep(1000); // let the coordinator fully process the first consumer's LeaveGroup

        System.out.println();
        System.out.println("--- Second consumer instance (same group): resumes from last COMMITTED offset ---");
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            int totalReprocessed = 0;
            for (int round = 0; round < 6 && totalReprocessed < 3; round++) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(3));
                for (ConsumerRecord<String, String> record : records) {
                    totalReprocessed++;
                    System.out.println("  reprocessing offset " + record.offset() + ": " + record.value());
                    commitRecord(consumer, record);
                }
            }
            System.out.println();
            System.out.println("Message 3 was reprocessed even though it was already handled once -");
            System.out.println("that's at-least-once delivery. An idempotent consumer is what makes this safe.");
        }
    }
}
