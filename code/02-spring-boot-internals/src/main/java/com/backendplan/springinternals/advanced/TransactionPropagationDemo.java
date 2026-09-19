package com.backendplan.springinternals.advanced;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

// REQUIRES_NEW suspends the caller's transaction and commits an independent one -
// so an audit record survives even when the outer business transaction rolls back.
public class TransactionPropagationDemo {

    static class AuditService {
        private final JdbcTemplate jdbc;
        AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        void recordAlways(String message) {
            jdbc.update("INSERT INTO audit_log(message) VALUES (?)", message);
        }
    }

    static class OrderService {
        private final JdbcTemplate jdbc;
        private final AuditService auditService;

        OrderService(JdbcTemplate jdbc, AuditService auditService) {
            this.jdbc = jdbc;
            this.auditService = auditService;
        }

        @Transactional
        void placeOrderThenFail() {
            jdbc.update("INSERT INTO orders(name) VALUES (?)", "order-that-will-be-rolled-back");
            auditService.recordAlways("attempted to place an order");
            throw new RuntimeException("payment declined - outer transaction rolls back");
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:propagation;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE orders(id IDENTITY PRIMARY KEY, name VARCHAR(255))");
            jdbcTemplate.execute("CREATE TABLE audit_log(id IDENTITY PRIMARY KEY, message VARCHAR(255))");
            return jdbcTemplate;
        }

        @Bean
        AuditService auditService(JdbcTemplate jdbcTemplate) {
            return new AuditService(jdbcTemplate);
        }

        @Bean
        OrderService orderService(JdbcTemplate jdbcTemplate, AuditService auditService) {
            return new OrderService(jdbcTemplate, auditService);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            OrderService orderService = ctx.getBean(OrderService.class);
            JdbcTemplate jdbcTemplate = ctx.getBean(JdbcTemplate.class);

            try {
                orderService.placeOrderThenFail();
            } catch (RuntimeException e) {
                System.out.println("Outer transaction failed as expected: " + e.getMessage());
            }

            Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
            Integer auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);

            System.out.println("Orders persisted (should be 0 - outer transaction rolled back): " + orderCount);
            System.out.println("Audit records persisted (should be 1 - REQUIRES_NEW committed independently): " + auditCount);
        }
    }
}
