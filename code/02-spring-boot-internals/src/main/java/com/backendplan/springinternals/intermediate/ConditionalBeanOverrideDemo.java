package com.backendplan.springinternals.intermediate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Mimics how Spring Boot's auto-configuration lets a user bean silently win:
// @ConditionalOnMissingBean only kicks in if nothing has already registered that bean type.
public class ConditionalBeanOverrideDemo {

    interface Clock {
        String now();
    }

    // stands in for a framework-provided "auto-configuration" class
    @Configuration
    static class FrameworkAutoConfiguration {
        @Bean
        @ConditionalOnMissingBean(Clock.class)
        Clock defaultClock() {
            return () -> "default-clock-value";
        }
    }

    // stands in for a user's own @Configuration class
    @Configuration
    static class UserConfig {
        @Bean
        Clock fixedClock() {
            return () -> "user-overridden-fixed-clock";
        }
    }

    // stands in for a conditional feature toggle, e.g. spring.feature.enabled=true
    @Configuration
    static class FeatureFlagAutoConfiguration {
        @Bean
        @ConditionalOnProperty(name = "feature.enabled", havingValue = "true")
        String featureBean() {
            return "feature is ON";
        }
    }

    public static void main(String[] args) {
        System.out.println("--- No user bean: auto-configuration's default wins ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(FrameworkAutoConfiguration.class);
            ctx.refresh();
            System.out.println("Clock value: " + ctx.getBean(Clock.class).now());
        }

        System.out.println("--- User bean present: it wins, auto-configuration backs off ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            // user config registered BEFORE the auto-configuration, mirroring real ordering
            ctx.register(UserConfig.class, FrameworkAutoConfiguration.class);
            ctx.refresh();
            System.out.println("Clock value: " + ctx.getBean(Clock.class).now());
        }

        System.out.println("--- Property-gated bean: absent unless the property is set ---");
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(FeatureFlagAutoConfiguration.class);
            ctx.refresh();
            boolean present = ctx.getBeanNamesForType(String.class).length > 0;
            System.out.println("Feature bean present without property: " + present);
        }
    }
}
