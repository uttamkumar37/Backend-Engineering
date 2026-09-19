package com.backendplan.datalayer.beginner;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Properties;

// Spring Data JPA at its simplest: an entity, a repository interface, no query code written.
public class JpaCrudBasics {

    @SpringBootApplication(exclude = MongoAutoConfiguration.class)
    static class App {}

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("spring.datasource.url", "jdbc:postgresql://localhost/backendplan_demo");
        props.put("spring.datasource.username", System.getProperty("user.name"));
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        props.put("spring.jpa.show-sql", "true");

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run(args)) {

            ProductRepository repository = ctx.getBean(ProductRepository.class);

            repository.save(new Product("Keyboard", 49.99));
            repository.save(new Product("Monitor", 199.99));
            repository.save(new Product("Mouse", 19.99));

            List<Product> all = repository.findAll();
            System.out.println("--- All products ---");
            all.forEach(System.out::println);
        }
    }
}
