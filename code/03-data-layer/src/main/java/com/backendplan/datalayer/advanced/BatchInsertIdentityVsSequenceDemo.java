package com.backendplan.datalayer.advanced;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

// hibernate.jdbc.batch_size is silently ignored for GenerationType.IDENTITY, because Hibernate
// must round-trip to the DB for each generated id before it can build the next insert.
// SEQUENCE (with a matching allocationSize) lets Hibernate actually batch the inserts.
public class BatchInsertIdentityVsSequenceDemo {

    private static final int ROW_COUNT = 3000;

    @SpringBootApplication(exclude = MongoAutoConfiguration.class)
    static class App {}

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("spring.datasource.url", "jdbc:postgresql://localhost/backendplan_demo");
        props.put("spring.datasource.username", System.getProperty("user.name"));
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        props.put("spring.jpa.show-sql", "false");
        props.put("spring.jpa.properties.hibernate.jdbc.batch_size", "50");
        props.put("spring.jpa.properties.hibernate.order_inserts", "true");
        props.put("spring.jpa.properties.hibernate.generate_statistics", "true");
        props.put("logging.level.org.hibernate.stat", "WARN");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run(args)) {

            BatchInsertService service = ctx.getBean(BatchInsertService.class);
            EntityManagerFactory emf = ctx.getBean(EntityManagerFactory.class);
            Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

            stats.clear();
            Instant t1 = Instant.now();
            service.insertIdentityRows(ROW_COUNT);
            Duration identityTime = Duration.between(t1, Instant.now());
            long identityBatches = stats.getPrepareStatementCount();

            stats.clear();
            Instant t2 = Instant.now();
            service.insertSequenceRows(ROW_COUNT);
            Duration sequenceTime = Duration.between(t2, Instant.now());
            long sequenceBatches = stats.getPrepareStatementCount();

            System.out.println("--- Inserting " + ROW_COUNT + " rows, batch_size=50 in both cases ---");
            System.out.println("IDENTITY: " + identityTime.toMillis() + " ms, " + identityBatches + " prepared statements");
            System.out.println("SEQUENCE: " + sequenceTime.toMillis() + " ms, " + sequenceBatches + " prepared statements");
            System.out.println();
            System.out.println("IDENTITY prepares roughly one statement per row (batching disabled).");
            System.out.println("SEQUENCE prepares far fewer - Hibernate can pre-allocate ids and batch inserts.");
        }
    }
}
