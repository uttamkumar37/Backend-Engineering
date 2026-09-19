package com.backendplan.datalayer.intermediate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Real EXPLAIN ANALYZE output from real Postgres - proving the leftmost-prefix rule
// and selectivity's effect on whether the planner even uses an index.
public class ExplainAnalyzeDemo {

    private static final String URL = "jdbc:postgresql://localhost/backendplan_demo";
    private static final String USER = System.getProperty("user.name");

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, "");
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS orders_bulk");
            stmt.execute("""
                    CREATE TABLE orders_bulk (
                        id BIGINT PRIMARY KEY,
                        tenant_id INT NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        created_at TIMESTAMP NOT NULL
                    )
                    """);

            // 200k rows: 100 tenants, mostly 'created' status (low selectivity), a spread of dates
            stmt.execute("""
                    INSERT INTO orders_bulk
                    SELECT
                        g,
                        g % 100,
                        CASE WHEN g % 1000 = 0 THEN 'cancelled' ELSE 'created' END,
                        TIMESTAMP '2024-01-01' + (g || ' seconds')::interval
                    FROM generate_series(1, 200000) AS g
                    """);
            stmt.execute("ANALYZE orders_bulk");

            System.out.println("=== 1. No index at all: sequential scan ===");
            explain(stmt, "SELECT * FROM orders_bulk WHERE tenant_id = 7 AND status = 'created'");

            stmt.execute("CREATE INDEX idx_status_tenant_created ON orders_bulk(status, tenant_id, created_at)");
            stmt.execute("ANALYZE orders_bulk");

            System.out.println("=== 2. Composite index (status, tenant_id, created_at): leftmost prefix used ===");
            explain(stmt, "SELECT * FROM orders_bulk WHERE status = 'cancelled' AND tenant_id = 7");

            System.out.println("=== 3. Same index, filtering on tenant_id alone: leftmost prefix violated ===");
            explain(stmt, "SELECT * FROM orders_bulk WHERE tenant_id = 7");

            System.out.println("=== 4. Filtering on the LOW-selectivity leading column alone ===");
            System.out.println("    'created' is ~99.9% of rows - the planner correctly prefers a seq scan");
            explain(stmt, "SELECT * FROM orders_bulk WHERE status = 'created'");

            System.out.println("=== 5. Filtering on the HIGH-selectivity value of the same column ===");
            System.out.println("    'cancelled' is ~0.1% of rows - the planner correctly uses the index");
            explain(stmt, "SELECT * FROM orders_bulk WHERE status = 'cancelled'");
        }
    }

    private static void explain(Statement stmt, String query) throws Exception {
        try (ResultSet rs = stmt.executeQuery("EXPLAIN (ANALYZE, FORMAT TEXT) " + query)) {
            while (rs.next()) {
                System.out.println("  " + rs.getString(1));
            }
        }
        System.out.println();
    }
}
