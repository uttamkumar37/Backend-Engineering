package com.backendplan.springinternals.advanced;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;

// Proves the AOP self-invocation bug: calling an @Transactional method via `this` bypasses
// the proxy entirely, so no transaction is opened. Calling the SAME method through the
// container-managed proxy (fetched from the context) opens a real transaction.
public class TransactionalSelfInvocationDemo {

    static class AuditService {
        boolean transactionActiveDuringDirectCall;
        boolean transactionActiveDuringProxiedCall;

        // calls b() via `this` - never goes through the proxy
        void callDirectly() {
            this.transactionalMethod(() -> transactionActiveDuringDirectCall =
                    TransactionSynchronizationManager.isActualTransactionActive());
        }

        @Transactional
        void transactionalMethod(Runnable check) {
            check.run();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:selfinvocation;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        AuditService auditService() {
            return new AuditService();
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            AuditService service = ctx.getBean(AuditService.class);

            System.out.println("--- Calling the @Transactional method via `this` (self-invocation) ---");
            service.callDirectly();
            System.out.println("Transaction active: " + service.transactionActiveDuringDirectCall
                    + "  <-- proxy was bypassed, annotation silently ignored");

            System.out.println("--- Calling the SAME method through the container-managed proxy ---");
            AuditService proxy = ctx.getBean(AuditService.class);
            proxy.transactionalMethod(() -> service.transactionActiveDuringProxiedCall =
                    TransactionSynchronizationManager.isActualTransactionActive());
            System.out.println("Transaction active: " + service.transactionActiveDuringProxiedCall
                    + "  <-- proxy intercepted the call, transaction opened as expected");
        }
    }
}
