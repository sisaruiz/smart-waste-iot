package org.unipi.smartwaste.db;

import java.sql.*;
import java.time.Instant;

public class DBDriver {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/smartwaste"; // Adjust host/port/dbname
    private static final String DB_USER = "your_username";
    private static final String DB_PASSWORD = "your_password";

    private Connection conn;

    public DBDriver() {
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            createTableIfNotExists();
            System.out.println("[DBDriver] Connected to MySQL database");
        } catch (SQLException e) {
            throw new RuntimeException("[DBDriver] Database connection error: " + e.getMessage(), e);
        }
    }

    private void createTableIfNotExists() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS sensor_readings ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "sensor_type VARCHAR(100) NOT NULL,"
                + "value VARCHAR(255) NOT NULL,"
                + "timestamp BIGINT NOT NULL"
                + ");";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    public void insertSensorReading(String sensorType, String value) {
        String insertSQL = "INSERT INTO sensor_readings(sensor_type, value, timestamp) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            pstmt.setString(1, sensorType);
            pstmt.setString(2, value);
            pstmt.setLong(3, Instant.now().getEpochSecond());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DBDriver] Failed to insert sensor reading: " + e.getMessage());
        }
    }

    public void close() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("[DBDriver] Database connection closed");
            } catch (SQLException e) {
                System.err.println("[DBDriver] Error closing database: " + e.getMessage());
            }
        }
    }
}
