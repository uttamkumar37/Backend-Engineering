package com.backendplan.microservices.intermediate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// The transactional outbox: the business row and the "event to publish" row are written in the
// SAME local database transaction, so an event can never be lost or published for a write that
// never actually committed. A separate polling step relays outbox rows to (a stand-in for) a broker.
public class OutboxPatternDemo {

    private static final String URL = "jdbc:postgresql://localhost/backendplan_demo";
    private static final String USER = System.getProperty("user.name");

    static void setUp(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders_outbox_demo");
            stmt.execute("DROP TABLE IF EXISTS outbox");
            stmt.execute("""
                    CREATE TABLE orders_outbox_demo (id SERIAL PRIMARY KEY, customer VARCHAR(100))
                    """);
            stmt.execute("""
                    CREATE TABLE outbox (
                        id SERIAL PRIMARY KEY,
                        event_type VARCHAR(100),
                        payload TEXT,
                        status VARCHAR(20) DEFAULT 'PENDING'
                    )
                    """);
        }
    }

    static void placeOrderWithOutboxEvent(Connection conn, String customer) throws Exception {
        conn.setAutoCommit(false);
        try {
            int orderId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders_outbox_demo (customer) VALUES (?) RETURNING id")) {
                ps.setString(1, customer);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    orderId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO outbox (event_type, payload) VALUES (?, ?)")) {
                ps.setString(1, "OrderCreated");
                ps.setString(2, "{\"orderId\":" + orderId + ",\"customer\":\"" + customer + "\"}");
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("Committed order " + orderId + " and its outbox event atomically.");
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // stands in for a message broker publish
    static List<String> fakeBrokerPublishedMessages = new ArrayList<>();

    static void relayPendingOutboxEvents(Connection conn) throws Exception {
        List<Integer> relayedIds = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, event_type, payload FROM outbox WHERE status = 'PENDING'")) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String eventType = rs.getString("event_type");
                String payload = rs.getString("payload");
                fakeBrokerPublishedMessages.add(eventType + ": " + payload);
                relayedIds.add(id);
            }
        }

        for (int id : relayedIds) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE outbox SET status = 'SENT' WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        }
        System.out.println("Relay run: published " + relayedIds.size() + " pending event(s) and marked them SENT.");
    }

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, "")) {
            setUp(conn);

            placeOrderWithOutboxEvent(conn, "Uttam Kumar");
            placeOrderWithOutboxEvent(conn, "Asha");

            System.out.println();
            relayPendingOutboxEvents(conn);
            System.out.println("Relay run again (nothing new to send): ");
            relayPendingOutboxEvents(conn);

            System.out.println();
            System.out.println("--- Messages the 'broker' actually received ---");
            fakeBrokerPublishedMessages.forEach(System.out::println);
        }
    }
}
