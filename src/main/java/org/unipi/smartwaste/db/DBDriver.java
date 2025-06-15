package org.unipi.smartwaste.db;

import java.sql.*;
import java.util.HashMap;

public class DBDriver {

    private static final String url = "jdbc:mysql://localhost:3306/SmartWasteDB?serverTimezone=Europe/Rome";
    private static final String username = "root";
    private static final String password = "ubuntu";

    // Update or insert actuator state (e.g. bin_full, anomaly_detected)
    public static int updateActuatorStatus(String address, String actuatorType, Boolean status) throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement(
            "REPLACE INTO actuators (ip, type, active) VALUES (?, ?, ?);"
        );
        ps.setString(1, address.substring(1)); // Remove leading slash from IP
        ps.setString(2, actuatorType);
        ps.setBoolean(3, status);
        int count = ps.executeUpdate();
        ps.close();
        connection.close();
        return count;
    }

    // Get actuator status by type
    public static HashMap<String, Object> getActuatorStatus(String actuatorType) throws SQLException {
        HashMap<String, Object> result = new HashMap<>();
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement(
            "SELECT ip, active FROM actuators WHERE type = ?"
        );
        ps.setString(1, actuatorType);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            result.put("ip", rs.getString("ip"));
            result.put("active", rs.getBoolean("active"));
        }
        rs.close();
        ps.close();
        connection.close();
        return result;
    }

    // Insert sensor data (used internally or directly)
    public static int insertSensorData(Long value, String sensorType) throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO data (value, sensor) VALUES (?, ?);"
        );
        ps.setInt(1, Math.toIntExact(value));
        ps.setString(2, sensorType);
        int count = ps.executeUpdate();
        ps.close();
        connection.close();
        return count;
    }

    // Insert sensor data (called by MQTTHandler)
    public static int insertData(Long value, String sensorType) throws SQLException {
        return insertSensorData(value, sensorType);
    }

    // Insert button press event (timestamp only)
    public static int insertButtonPress() throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement(
            "INSERT INTO button_events (event_time) VALUES (CURRENT_TIMESTAMP);"
        );
        int count = ps.executeUpdate();
        ps.close();
        connection.close();
        return count;
    }

    // Get latest value for each sensor
    public static HashMap<String, Integer> getLatestSensorData() throws SQLException {
        HashMap<String, Integer> result = new HashMap<>();
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement(
            "SELECT sensor, value FROM data " +
            "WHERE (sensor, timestamp) IN " +
            "(SELECT sensor, MAX(timestamp) FROM data GROUP BY sensor)"
        );
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            result.put(rs.getString("sensor"), rs.getInt("value"));
        }
        rs.close();
        ps.close();
        connection.close();
        return result;
    }

    // Reset actuators table (clear all records)
    public static void resetActuators() throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        PreparedStatement ps = connection.prepareStatement("DELETE FROM actuators");
        ps.executeUpdate();
        ps.close();
        connection.close();
    }
}
