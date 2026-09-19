package com.backendplan.kafka.advanced;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// A "poison pill" message must never block the healthy messages behind it on the same partition.
// Non-blocking retry: failed messages go to a separate retry topic instead of being retried
// in place; after exhausting retries, they land in a DLQ instead of looping forever.
public class RetryTopicAndDlqDemo {

    private static final String MAIN_TOPIC = "payments-main";
    private static final String RETRY_TOPIC = "payments-retry";
    private static final String DLQ_TOPIC = "payments-dlq";
    private static final int MAX_RETRIES = 2;

    static boolean processes(String value) {
        return !value.equals("poison"); // simulates a message that will NEVER succeed
    }

    static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }

    static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    static int retryCountOf(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("retry-count");
        return header == null ? 0 : Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", "localhost:9092");
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.createTopics(List.of(
                    new NewTopic(MAIN_TOPIC, 1, (short) 1),
                    new NewTopic(RETRY_TOPIC, 1, (short) 1),
                    new NewTopic(DLQ_TOPIC, 1, (short) 1)
            )).all().get();
        } catch (Exception ignored) {}

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            List<String> messages = List.of("payment-1", "poison", "payment-2", "payment-3");
            for (String msg : messages) {
                producer.send(new ProducerRecord<>(MAIN_TOPIC, msg, msg)).get();
            }
        }

        System.out.println("--- Processing main topic: poison message goes to retry topic, others succeed ---");
        List<String> succeeded = new ArrayList<>();
        try (KafkaConsumer<String, String> mainConsumer = new KafkaConsumer<>(consumerProps("main-group"));
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            mainConsumer.subscribe(List.of(MAIN_TOPIC));
            ConsumerRecords<String, String> records = mainConsumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, String> record : records) {
                if (processes(record.value())) {
                    succeeded.add(record.value());
                    System.out.println("  processed on main topic: " + record.value());
                } else {
                    System.out.println("  FAILED on main topic: " + record.value() + " -> routing to retry topic");
                    List<Header> headers = List.of(new RecordHeader("retry-count", "1".getBytes(StandardCharsets.UTF_8)));
                    producer.send(new ProducerRecord<>(RETRY_TOPIC, null, record.key(), record.value(), headers)).get();
                }
            }
        }
        System.out.println("Main topic kept moving - " + succeeded.size() + " healthy messages processed without waiting on the poison one.");

        System.out.println();
        System.out.println("--- Processing retry topic: still fails, exhausts retries, goes to DLQ ---");
        // multiple poll rounds - a re-queued message needs to come back around through the same topic
        try (KafkaConsumer<String, String> retryConsumer = new KafkaConsumer<>(consumerProps("retry-group"));
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            retryConsumer.subscribe(List.of(RETRY_TOPIC));
            for (int round = 0; round < MAX_RETRIES + 2; round++) {
                ConsumerRecords<String, String> records = retryConsumer.poll(Duration.ofSeconds(3));
                if (records.isEmpty()) {
                    continue;
                }
                for (ConsumerRecord<String, String> record : records) {
                    int attempt = retryCountOf(record);
                    if (processes(record.value())) {
                        System.out.println("  recovered on retry attempt " + attempt + ": " + record.value());
                    } else if (attempt >= MAX_RETRIES) {
                        System.out.println("  exhausted " + MAX_RETRIES + " retries for '" + record.value() + "' -> routing to DLQ");
                        producer.send(new ProducerRecord<>(DLQ_TOPIC, record.key(), record.value())).get();
                    } else {
                        System.out.println("  still failing on attempt " + attempt + ", re-queuing to retry topic");
                        List<Header> headers = List.of(new RecordHeader("retry-count",
                                String.valueOf(attempt + 1).getBytes(StandardCharsets.UTF_8)));
                        producer.send(new ProducerRecord<>(RETRY_TOPIC, null, record.key(), record.value(), headers)).get();
                    }
                }
            }
        }

        System.out.println();
        System.out.println("--- DLQ contents ---");
        try (KafkaConsumer<String, String> dlqConsumer = new KafkaConsumer<>(consumerProps("dlq-group"))) {
            dlqConsumer.subscribe(List.of(DLQ_TOPIC));
            ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, String> record : records) {
                System.out.println("  " + record.value() + " (needs a human, or a fix, before replay)");
            }
        }
    }
}
