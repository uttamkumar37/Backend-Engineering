package com.backendplan.kafka.advanced;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// Kafka's transactional producer makes the output message AND the input offset commit atomic -
// but only within Kafka. This demo proves the atomicity: an aborted transaction's output is
// invisible to read_committed consumers, exactly as if that message was never produced at all.
public class ExactlyOnceTransactionalDemo {

    private static final String INPUT_TOPIC = "eos-input";
    private static final String OUTPUT_TOPIC = "eos-output";

    public static void main(String[] args) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", "localhost:9092");
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.createTopics(List.of(
                    new NewTopic(INPUT_TOPIC, 1, (short) 1),
                    new NewTopic(OUTPUT_TOPIC, 1, (short) 1)
            )).all().get();
        } catch (Exception ignored) {}

        Properties producerBootstrap = new Properties();
        producerBootstrap.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerBootstrap.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerBootstrap.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> seed = new KafkaProducer<>(producerBootstrap)) {
            seed.send(new ProducerRecord<>(INPUT_TOPIC, "k1", "will-commit")).get();
            seed.send(new ProducerRecord<>(INPUT_TOPIC, "k2", "will-abort")).get();
        }

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "eos-demo-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        Properties txProducerProps = new Properties();
        txProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        txProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        txProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        txProducerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "eos-demo-tx-1");
        txProducerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
             Producer<String, String> txProducer = new KafkaProducer<>(txProducerProps)) {

            txProducer.initTransactions();
            consumer.subscribe(List.of(INPUT_TOPIC));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            for (ConsumerRecord<String, String> record : records) {
                txProducer.beginTransaction();
                try {
                    String outputValue = "processed:" + record.value();
                    txProducer.send(new ProducerRecord<>(OUTPUT_TOPIC, record.key(), outputValue));

                    Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                    offsets.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1));
                    txProducer.sendOffsetsToTransaction(offsets,
                            new org.apache.kafka.clients.consumer.ConsumerGroupMetadata("eos-demo-group"));

                    if (record.value().equals("will-abort")) {
                        throw new RuntimeException("simulated failure mid-transaction");
                    }

                    txProducer.commitTransaction();
                    System.out.println("Committed transaction for: " + record.value());
                } catch (Exception e) {
                    txProducer.abortTransaction();
                    System.out.println("Aborted transaction for: " + record.value() + " (" + e.getMessage() + ")");
                }
            }
        }

        System.out.println();
        System.out.println("--- Reading output topic with isolation.level=read_committed ---");
        try (KafkaConsumer<String, String> outputConsumer = new KafkaConsumer<>(consumerProps)) {
            outputConsumer.subscribe(List.of(OUTPUT_TOPIC));
            ConsumerRecords<String, String> outputRecords = outputConsumer.poll(Duration.ofSeconds(5));
            System.out.println("Visible messages: " + outputRecords.count() + " (expected: 1 - only the committed one)");
            for (ConsumerRecord<String, String> record : outputRecords) {
                System.out.println("  " + record.value());
            }
            System.out.println("The aborted transaction's message for 'will-abort' is invisible, as if it never happened.");
        }
    }
}
