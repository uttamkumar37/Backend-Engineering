package com.backendplan.springinternals.advanced;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;

// Default @Transactional rollback rule: rolls back on unchecked exceptions,
// does NOT roll back on checked exceptions unless rollbackFor is specified.
public class RollbackRulesDemo {

    static class LedgerService {
        private final JdbcTemplate jdbc;
        LedgerService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

        @Transactional
        void insertThenThrowUnchecked() {
            jdbc.update("INSERT INTO ledger(entry) VALUES (?)", "unchecked-exception-attempt");
            throw new RuntimeException("boom - unchecked");
        }

        // checked exception - Spring's default rollback rule does NOT cover this
        @Transactional
        void insertThenThrowChecked() throws IOException {
            jdbc.update("INSERT INTO ledger(entry) VALUES (?)", "checked-exception-attempt-default-rule");
            throw new IOException("boom - checked, not rolled back by default");
        }

        @Transactional(rollbackFor = IOException.class)
        void insertThenThrowCheckedWithRollbackFor() throws IOException {
            jdbc.update("INSERT INTO ledger(entry) VALUES (?)", "checked-exception-attempt-explicit-rollback");
            throw new IOException("boom - checked, but explicitly configured to roll back");
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:rollbackrules;DB_CLOSE_DELAY=-1");
            return ds;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute("CREATE TABLE ledger(id IDENTITY PRIMARY KEY, entry VARCHAR(255))");
            return jdbcTemplate;
        }

        @Bean
        LedgerService ledgerService(JdbcTemplate jdbcTemplate) {
            return new LedgerService(jdbcTemplate);
        }
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Config.class)) {
            LedgerService service = ctx.getBean(LedgerService.class);
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            try {
                service.insertThenThrowUnchecked();
            } catch (RuntimeException e) {
                System.out.println("Unchecked exception thrown: " + e.getMessage());
            }

            try {
                service.insertThenThrowChecked();
            } catch (IOException e) {
                System.out.println("Checked exception thrown (default rule): " + e.getMessage());
            }

            try {
                service.insertThenThrowCheckedWithRollbackFor();
            } catch (IOException e) {
                System.out.println("Checked exception thrown (rollbackFor configured): " + e.getMessage());
            }

            System.out.println("--- Final ledger contents (rows that actually committed) ---");
            jdbc.queryForList("SELECT entry FROM ledger", String.class)
                    .forEach(System.out::println);
            System.out.println("Expected: only the DEFAULT-RULE checked-exception row survives -");
            System.out.println("the unchecked one rolled back, and the explicit rollbackFor one rolled back too.");
        }
    }
}
