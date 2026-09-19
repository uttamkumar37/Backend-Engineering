package com.backendplan.microservices.advanced;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// CQRS without event sourcing: writes go to a normalized model, an async projector denormalizes
// into a read-optimized table. The read model is eventually consistent - querying it immediately
// after a write can legitimately return stale or missing data. That's not a bug to hide from the
// demo; it's the actual trade-off CQRS makes.
public class CqrsProjectionDemo {

    private static final String URL = "jdbc:postgresql://localhost/backendplan_demo";
    private static final String USER = System.getProperty("user.name");

    static void setUp(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders_write_model");
            stmt.execute("DROP TABLE IF EXISTS orders_read_model");
            stmt.execute("CREATE TABLE orders_write_model (id SERIAL PRIMARY KEY, customer VARCHAR(100), total NUMERIC)");
            stmt.execute("CREATE TABLE orders_read_model (id INT PRIMARY KEY, summary TEXT)");
        }
    }

    static int writeOrder(Connection conn, String customer, double total) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO orders_write_model (customer, total) VALUES (?, ?) RETURNING id")) {
            ps.setString(1, customer);
            ps.setDouble(2, total);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static void projectToReadModel(int orderId) {
        try (Connection conn = DriverManager.getConnection(URL, USER, "")) {
            try (PreparedStatement select = conn.prepareStatement(
                    "SELECT customer, total FROM orders_write_model WHERE id = ?")) {
                select.setInt(1, orderId);
                try (ResultSet rs = select.executeQuery()) {
                    rs.next();
                    String summary = rs.getString("customer") + " - $" + rs.getDouble("total");
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO orders_read_model (id, summary) VALUES (?, ?)")) {
                        insert.setInt(1, orderId);
                        insert.setString(2, summary);
                        insert.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String queryReadModel(int orderId) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, "");
             PreparedStatement ps = conn.prepareStatement("SELECT summary FROM orders_read_model WHERE id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("summary") : null;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, "")) {
            setUp(conn);

            int orderId = writeOrder(conn, "Uttam Kumar", 499.00);
            System.out.println("Wrote order " + orderId + " to the write model.");

            // the projector runs asynchronously, with a deliberate delay, exactly like a real
            // outbox-driven or CDC-driven projection would have real (if usually much smaller) lag
            ScheduledExecutorService projector = Executors.newSingleThreadScheduledExecutor();
            projector.schedule(() -> projectToReadModel(orderId), 300, TimeUnit.MILLISECONDS);

            System.out.println("Querying the read model IMMEDIATELY after the write:");
            System.out.println("  -> " + queryReadModel(orderId) + "  (null: projection hasn't run yet)");

            Thread.sleep(500);

            System.out.println("Querying the read model AFTER the projection has caught up:");
            System.out.println("  -> " + queryReadModel(orderId));

            projector.shutdown();
        }
    }
}
