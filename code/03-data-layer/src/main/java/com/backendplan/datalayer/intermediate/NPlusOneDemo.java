package com.backendplan.datalayer.intermediate;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Properties;

// The N+1 problem, made visible with real Hibernate statistics, not a claim to take on faith:
// 1 query to fetch all authors, then 1 additional query PER author to lazily fetch their books.
public class NPlusOneDemo {

    @SpringBootApplication(exclude = MongoAutoConfiguration.class)
    static class App {}

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("spring.datasource.url", "jdbc:postgresql://localhost/backendplan_demo");
        props.put("spring.datasource.username", System.getProperty("user.name"));
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        props.put("spring.jpa.show-sql", "true");
        props.put("spring.jpa.properties.hibernate.generate_statistics", "true");
        props.put("logging.level.org.hibernate.stat", "WARN");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run(args)) {

            AuthorRepository authorRepository = ctx.getBean(AuthorRepository.class);
            BookRepository bookRepository = ctx.getBean(BookRepository.class);
            EntityManagerFactory emf = ctx.getBean(EntityManagerFactory.class);
            SessionFactory sessionFactory = emf.unwrap(SessionFactory.class);
            Statistics stats = sessionFactory.getStatistics();

            for (int i = 1; i <= 5; i++) {
                Author author = authorRepository.save(new Author("Author-" + i));
                for (int b = 1; b <= 3; b++) {
                    bookRepository.save(new Book("Book-" + i + "-" + b, author));
                }
            }

            stats.clear();
            System.out.println("--- Fetching 5 authors and accessing their book collections (LAZY) ---");
            AuthorService service = ctx.getBean(AuthorService.class);
            int totalBooks = service.accessAllBooksNaively();

            System.out.println("Total books counted: " + totalBooks);
            System.out.println("Prepared statements executed: " + stats.getPrepareStatementCount());
            System.out.println("Expected: 1 (fetch all authors) + 5 (one lazy load per author) = 6");
        }
    }
}
