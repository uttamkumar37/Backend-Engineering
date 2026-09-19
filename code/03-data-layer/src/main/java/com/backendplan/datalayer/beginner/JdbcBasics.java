package com.backendplan.datalayer.beginner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

// Plain JDBC - no ORM, no Spring. The fundamentals every abstraction above this sits on.
public class JdbcBasics {

    private static final String URL = "jdbc:postgresql://localhost/backendplan_demo";
    private static final String USER = System.getProperty("user.name");

    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, USER, "");
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS students");
            stmt.execute("CREATE TABLE students (id SERIAL PRIMARY KEY, name VARCHAR(100), grade INT)");

            stmt.executeUpdate("INSERT INTO students (name, grade) VALUES ('Asha', 90)");
            stmt.executeUpdate("INSERT INTO students (name, grade) VALUES ('Ravi', 72)");
            stmt.executeUpdate("INSERT INTO students (name, grade) VALUES ('Meera', 85)");

            try (ResultSet rs = stmt.executeQuery("SELECT id, name, grade FROM students WHERE grade > 80 ORDER BY grade DESC")) {
                System.out.println("Students with grade > 80:");
                while (rs.next()) {
                    System.out.printf("  #%d %s - %d%n", rs.getInt("id"), rs.getString("name"), rs.getInt("grade"));
                }
            }
        }
    }
}
