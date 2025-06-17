package org.unipi.smartwaste.db;

import java.sql.*;
import java.util.*;

public class DBDriver {

    private static final String url = "jdbc:mysql://localhost:3306/SmartWasteDB?serverTimezone=Europe/Rome";
    private static final String username = "root";
    private static final String password = "ubuntu";

    // Update or insert actuator status for a given IP and actuator type
    public static int updateActuatorStatus(String ip, String actuatorType, Boolean status) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "REPLACE INTO actuators (ip, type, active) VALUES (?, ?, ?);"
             )) {
            if (ip.startsWith("/")) {
                ip = ip.substring(1);
            }
            ps.setString(1, ip);
            ps.setString(2, actuatorType);
            ps.setBoolean(3, status);
            return ps.executeUpdate();
        }
    }

    // Retrieve actuator status by actuator type (returns first found)
    public static HashMap<String, Object> retrieveActuator(String actuatorType) throws SQLException {
        HashMap<String, Object> result = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT ip, active FROM actuators WHERE type = ? LIMIT 1"
             )) {
            ps.setString(1, actuatorType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("ip", rs.getString("ip"));
                    result.put("active", rs.getBoolean("active"));
                }
            }
        }
        return result;
    }

    /**
     * Retrieve status of all bins, including sensor data and actuator statuses
     * Returned List contains one Map per bin with keys:
     * - id (String)
     * - ip (String)
     * - fill_level (Integer)
     * - temperature (Integer)
     * - humidity (Integer)
     * - anomaly_active (Boolean)
     * - full_active (Boolean)
     */
    public static List<HashMap<String, Object>> retrieveAllBinsStatus() throws SQLException {
        List<HashMap<String, Object>> binsList = new ArrayList<>();

        String sql = "SELECT b.id, b.ip, " +
                     "  ds_fill.value AS fill_level, " +
                     "  ds_temp.value AS temperature, " +
                     "  ds_hum.value AS humidity, " +
                     "  (SELECT a.active FROM actuators a WHERE a.ip = b.ip AND a.type = 'anomaly' LIMIT 1) AS anomaly_active, " +
                     "  (SELECT a.active FROM actuators a WHERE a.ip = b.ip AND a.type = 'full' LIMIT 1) AS full_active " +
                     "FROM bins b " +
                     "LEFT JOIN data ds_fill ON ds_fill.sensor = 'fill_level' AND ds_fill.timestamp = (" +
                     "    SELECT MAX(timestamp) FROM data WHERE sensor = 'fill_level' AND sensor_ip = b.ip" +
                     ") " +
                     "LEFT JOIN data ds_temp ON ds_temp.sensor = 'temperature' AND ds_temp.timestamp = (" +
                     "    SELECT MAX(timestamp) FROM data WHERE sensor = 'temperature' AND sensor_ip = b.ip" +
                     ") " +
                     "LEFT JOIN data ds_hum ON ds_hum.sensor = 'humidity' AND ds_hum.timestamp = (" +
                     "    SELECT MAX(timestamp) FROM data WHERE sensor = 'humidity' AND sensor_ip = b.ip" +
                     ")";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                HashMap<String, Object> bin = new HashMap<>();

                bin.put("id", rs.getString("id"));
                bin.put("ip", rs.getString("ip"));

                // The sensor values may be null if no data present
                Integer fillLevel = rs.getObject("fill_level") != null ? rs.getInt("fill_level") : null;
                Integer temperature = rs.getObject("temperature") != null ? rs.getInt("temperature") : null;
                Integer humidity = rs.getObject("humidity") != null ? rs.getInt("humidity") : null;

                bin.put("fill_level", fillLevel);
                bin.put("temperature", temperature);
                bin.put("humidity", humidity);

                Boolean anomalyActive = rs.getObject("anomaly_active") != null ? rs.getBoolean("anomaly_active") : false;
                Boolean fullActive = rs.getObject("full_active") != null ? rs.getBoolean("full_active") : false;

                bin.put("anomaly_active", anomalyActive);
                bin.put("full_active", fullActive);

                binsList.add(bin);
            }
        }

        return binsList;
    }

    // Insert sensor data (value + sensor type)
    public static int insertSensorData(Long value, String sensorType) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO data (value, sensor) VALUES (?, ?);"
             )) {
            ps.setInt(1, Math.toIntExact(value));
            ps.setString(2, sensorType);
            return ps.executeUpdate();
        }
    }

    // Alias method for insertSensorData (used by MQTTHandler)
    public static int insertData(Long value, String sensorType) throws SQLException {
        return insertSensorData(value, sensorType);
    }

    // Insert button press event with current timestamp
    public static int insertButtonPress() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO button_events (event_time) VALUES (CURRENT_TIMESTAMP);"
             )) {
            return ps.executeUpdate();
        }
    }

    // Retrieve latest sensor data per sensor type
    public static HashMap<String, Integer> retrieveData() throws SQLException {
        HashMap<String, Integer> latestValues = new HashMap<>();
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT sensor, value FROM data " +
                 "WHERE (sensor, timestamp) IN (" +
                 "SELECT sensor, MAX(timestamp) FROM data GROUP BY sensor" +
                 ")"
             );
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                latestValues.put(rs.getString("sensor"), rs.getInt("value"));
            }
        }
        return latestValues;
    }

    // Clear actuators table (useful for testing or reset)
    public static void resetActuators() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement("DELETE FROM actuators")) {
            ps.executeUpdate();
        }
    }
}
