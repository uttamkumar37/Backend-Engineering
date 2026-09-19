package com.backendplan.springinternals.beginner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

// The smallest useful demo of dependency injection and bean lifecycle:
// a component depending on another component, wired by the container, not by `new`.
public class BeginnerApp {

    interface MessageProvider {
        String message();
    }

    @Component
    static class DefaultMessageProvider implements MessageProvider {
        @Override
        public String message() {
            return "Hello from a Spring-managed bean";
        }
    }

    @Component
    static class GreetingService {
        private final MessageProvider messageProvider;

        // constructor injection - the container resolves and passes the dependency
        GreetingService(MessageProvider messageProvider) {
            this.messageProvider = messageProvider;
        }

        void greet() {
            System.out.println(messageProvider.message());
        }
    }

    @Configuration
    static class AppConfig {
        @Bean
        DefaultMessageProvider defaultMessageProvider() {
            return new DefaultMessageProvider();
        }

        @Bean
        GreetingService greetingService(MessageProvider messageProvider) {
            return new GreetingService(messageProvider);
        }

        @Bean
        CommandLineRunner runner(GreetingService greetingService) {
            return args -> greetingService.greet();
        }
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(AppConfig.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
