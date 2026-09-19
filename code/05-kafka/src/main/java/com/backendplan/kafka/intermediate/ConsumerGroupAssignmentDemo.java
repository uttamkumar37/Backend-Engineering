package com.backendplan.kafka.intermediate;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

// Partition count is the hard ceiling on consumer parallelism within a group: with 4 partitions
// and 2 consumers, each consumer gets roughly half. A 3rd consumer in the same group would sit idle.
public class ConsumerGroupAssignmentDemo {

    private static final String TOPIC = "assignment-demo";
    private static final String GROUP = "assignment-demo-group";

    static KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(TOPIC));
        return consumer;
    }

    public static void main(String[] args) throws Exception {
        Properties adminProps = new Properties();
        adminProps.put("bootstrap.servers", "localhost:9092");
        try (AdminClient admin = AdminClient.create(adminProps)) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 4, (short) 1))).all().get();
        } catch (Exception ignored) {}

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < 20; i++) {
                producer.send(new ProducerRecord<>(TOPIC, "key-" + i, "msg-" + i)).get();
            }
        }

        KafkaConsumer<String, String> consumerA = newConsumer();
        KafkaConsumer<String, String> consumerB = newConsumer();

        // a few empty polls let the group finish its join/rebalance protocol
        for (int i = 0; i < 8; i++) {
            consumerA.poll(Duration.ofMillis(1000));
            consumerB.poll(Duration.ofMillis(1000));
            if (!consumerA.assignment().isEmpty() && !consumerB.assignment().isEmpty()) {
                break;
            }
        }

        Set<TopicPartition> assignedToA = consumerA.assignment();
        Set<TopicPartition> assignedToB = consumerB.assignment();

        System.out.println("4 partitions, 2 consumers in the same group '" + GROUP + "':");
        System.out.println("  Consumer A assigned partitions: " + partitionNumbers(assignedToA));
        System.out.println("  Consumer B assigned partitions: " + partitionNumbers(assignedToB));
        System.out.println("  Total partitions covered: " + (assignedToA.size() + assignedToB.size()) + " / 4");

        consumerA.close();
        consumerB.close();
    }

    private static List<Integer> partitionNumbers(Set<TopicPartition> partitions) {
        return partitions.stream().map(TopicPartition::partition).sorted().toList();
    }
}
