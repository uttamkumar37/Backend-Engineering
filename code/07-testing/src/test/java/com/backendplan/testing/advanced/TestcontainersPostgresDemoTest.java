package com.backendplan.testing.advanced;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

// NOT run in this environment - Docker is not installed here, and Testcontainers requires it.
// This is exactly the point Topic 3 and Topic 7's concept docs make about H2: an in-memory
// substitute diverges from real Postgres behavior (JSON columns, specific constraint error
// types, window functions), which is why this test runs against the REAL Postgres image, not
// a fake. Run it with a local Docker daemon available: `mvn test -Dtest=TestcontainersPostgresDemoTest`.
@Testcontainers
class TestcontainersPostgresDemoTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void queriesARealPostgresInstanceNotAFake() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            // JSONB is real Postgres-specific behavior H2 does not faithfully emulate -
            // exactly the divergence the concept doc warns about
            stmt.execute("CREATE TABLE events (id SERIAL PRIMARY KEY, payload JSONB)");
            stmt.execute("INSERT INTO events (payload) VALUES ('{\"type\": \"OrderCreated\"}')");

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT payload->>'type' AS event_type FROM events")) {
                rs.next();
                assertEquals("OrderCreated", rs.getString("event_type"));
            }
        }
    }
}
