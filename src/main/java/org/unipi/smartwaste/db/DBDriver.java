package org.unipi.smartwaste.db;

import java.sql.*;
import java.util.HashMap;

public class DBDriver {

    private static final String url = "jdbc:mysql://localhost:3306/SmartWasteDB?serverTimezone=Europe/Rome";
    private static final String username = "root";
    private static final String password = "ubuntu";

    public static int updateActuators(String address, String actuatorType, Boolean status) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "REPLACE INTO actuators (ip, type, active) VALUES (?, ?, ?);")) {

            if (address.startsWith("/")) {
                address = address.substring(1);
            }
            ps.setString(1, address);
            ps.setString(2, actuatorType);
            ps.setBoolean(3, status);

            ps.executeUpdate();
            return ps.getUpdateCount();
        }
    }

    /**
     * Retrieve actuator info by actuator type.
     * Returns empty HashMap if none found.
     */
    public static HashMap<String, Object> retrieveActuator(String actuatorType) throws SQLException {
        HashMap<String, Object> result = new HashMap<>();

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "SELECT ip, active FROM actuators WHERE type = ? LIMIT 1")) {

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
     * Insert sensor data with value and sensor type.
     */
    public static int insertData(Long value, String type) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(
                 "INSERT INTO data (value, sensor) VALUES (?, ?);")) {

            ps.setInt(1, Math.toIntExact(value));
            ps.setString(2, type);
            ps.executeUpdate();
            return ps.getUpdateCount();
        }
    }

    /**
     * Retrieve latest sensor values for all sensors.
     * Returns map of sensor type to latest value.
     */
    public static HashMap<String, Integer> retrieveData() throws SQLException {
        HashMap<String, Integer> result = new HashMap<>();

        String sql = "SELECT sensor, value FROM data " +
                     "WHERE (sensor, timestamp) IN " +
                     "(SELECT sensor, MAX(timestamp) FROM data GROUP BY sensor)";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.put(rs.getString("sensor"), rs.getInt("value"));
            }
        }
        return result;
    }

    /**
     * Retrieve the single bin status including sensor values and actuator states.
     * Keys returned:
     * - fill_level (Integer or null)
     * - temperature (Integer or null)
     * - humidity (Integer or null)
     * - anomaly_fire_active (Boolean)
     * - anomaly_leakage_active (Boolean)
     * - full_active (Boolean)
     */
    public static HashMap<String, Object> getBinStatus() throws SQLException {
        HashMap<String, Object> bin = new HashMap<>();

        String sql = "SELECT " +
                "  (SELECT value FROM data WHERE sensor = 'fill_level' ORDER BY timestamp DESC LIMIT 1) AS fill_level, " +
                "  (SELECT value FROM data WHERE sensor = 'temperature' ORDER BY timestamp DESC LIMIT 1) AS temperature, " +
                "  (SELECT value FROM data WHERE sensor = 'humidity' ORDER BY timestamp DESC LIMIT 1) AS humidity, " +
                "  (SELECT active FROM actuators WHERE type = 'anomaly_fire' LIMIT 1) AS anomaly_fire_active, " +
                "  (SELECT active FROM actuators WHERE type = 'anomaly_leakage' LIMIT 1) AS anomaly_leakage_active, " +
                "  (SELECT active FROM actuators WHERE type = 'full' LIMIT 1) AS full_active";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                bin.put("fill_level", rs.getObject("fill_level") != null ? rs.getInt("fill_level") : null);
                bin.put("temperature", rs.getObject("temperature") != null ? rs.getInt("temperature") : null);
                bin.put("humidity", rs.getObject("humidity") != null ? rs.getInt("humidity") : null);

                bin.put("anomaly_fire_active", rs.getObject("anomaly_fire_active") != null && rs.getBoolean("anomaly_fire_active"));
                bin.put("anomaly_leakage_active", rs.getObject("anomaly_leakage_active") != null && rs.getBoolean("anomaly_leakage_active"));
                bin.put("full_active", rs.getObject("full_active") != null && rs.getBoolean("full_active"));
            }
        }
        return bin;
    }

    public static void resetActuators() throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = connection.prepareStatement("DELETE FROM actuators")) {
            ps.executeUpdate();
        }
    }

    public static Integer getLatestSensorValue(String sensor) throws SQLException {
        String sql = "SELECT value FROM data WHERE sensor = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection connection = DriverManager.getConnection(url, username, password);
            PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sensor);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("value");
                } else {
                    return null;
                }
            }
        }
    }

}
